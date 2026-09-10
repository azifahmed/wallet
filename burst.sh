#!/usr/bin/env bash
# ============================================================
# Wallet Transfer — Burst Concurrency Test (client-side probe)
# Usage: bash burst.sh <BASE_URL>
#
# This script is independent of where the API is hosted. Point it at
# localhost after `docker compose up`, or at a public Render/Railway URL.
# Deployment (Dockerfile / compose / host) is a separate concern.
# ============================================================
set -euo pipefail
BASE_URL="${1:-https://wallet-transfer-mleu.onrender.com}"
# Strip trailing slash so paths join cleanly
BASE_URL="${BASE_URL%/}"

# Swap these for fresh users (e.g. token-user3 / token-user4) without editing call sites.
TOKEN_USER1="${TOKEN_USER1:-token-user1}"
TOKEN_USER2="${TOKEN_USER2:-token-user2}"
TOKEN_USER_CONCURRENT="${TOKEN_USER_CONCURRENT:-token-user5}"  # TEST 1: concurrent get-or-create
TOKEN_ADMIN="${TOKEN_ADMIN:-token-admin}"

PASS=0; FAIL=0
GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }

echo "==============================="
echo " Wallet Burst Tests: $BASE_URL"
echo "==============================="

echo ""
echo "--- Bootstrap ---"
echo "Tokens: user1=$TOKEN_USER1  user2=$TOKEN_USER2  concurrent=$TOKEN_USER_CONCURRENT"
W1=$(curl -sf -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $TOKEN_USER1" | jq -r '.walletId')
W2=$(curl -sf -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $TOKEN_USER2" | jq -r '.walletId')
echo "W1=$W1  W2=$W2"

# Mint/load for TEST 2/3 (admin credit — outside P2P conservation, like Add Money)
SEED_PAISE=1000000
echo ""
echo "--- Seed (admin credit ${SEED_PAISE} paise each) ---"
curl -sf -X POST "$BASE_URL/wallets/$W1/credit" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d "{\"amount_paise\":${SEED_PAISE}}" | jq -c '{walletId,balance}'
curl -sf -X POST "$BASE_URL/wallets/$W2/credit" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d "{\"amount_paise\":${SEED_PAISE}}" | jq -c '{walletId,balance}'
ok "Seeded W1 and W2 via admin credit"

echo ""
echo "--- TEST 1: 50 concurrent POST /wallets for same user ---"
WALLET_IDS=$(seq 1 50 | xargs -P50 -I{} \
  curl -s -X POST "$BASE_URL/wallets" -H "Authorization: Bearer $TOKEN_USER_CONCURRENT" 2>/dev/null \
  | jq -r '.walletId // empty' 2>/dev/null | sort -u | wc -l | tr -d ' ' || true)
WALLET_IDS=${WALLET_IDS:-0}
if [ "$WALLET_IDS" -eq 1 ]; then
  ok "TEST 1: Exactly 1 wallet ID across 50 concurrent creates"
else
  fail "TEST 1: Got $WALLET_IDS distinct wallet IDs — expected 1"
fi

echo ""
echo "--- Pre-check: funded balances for TEST 2/3 ---"
FUND_W1=$(curl -sf "$BASE_URL/wallets/$W1" -H "Authorization: Bearer $TOKEN_USER1" | jq -r '.balance')
FUND_W2=$(curl -sf "$BASE_URL/wallets/$W2" -H "Authorization: Bearer $TOKEN_USER2" | jq -r '.balance')
echo "Current balances: W1=$FUND_W1  W2=$FUND_W2"
MIN_FUND=50000
if [ "${FUND_W1:-0}" -lt "$MIN_FUND" ] || [ "${FUND_W2:-0}" -lt "$MIN_FUND" ]; then
  fail "Wallets underfunded for TEST 2/3 (need ≥ ${MIN_FUND} paise each after admin credit)."
  echo ""
  echo "==============================="
  echo " Results: ${PASS} passed, ${FAIL} failed"
  echo "==============================="
  exit "$FAIL"
fi
ok "Wallets funded enough for contention probes"

echo ""
echo "--- TEST 2: 30 concurrent POSTs with same idempotency_key ---"
IDEM_KEY="idem-$(date +%s%N)"
RESPONSES=$(seq 1 30 | xargs -P30 -I{} \
  curl -sf -X POST "$BASE_URL/transfers" \
  -H "Authorization: Bearer $TOKEN_USER1" \
  -H "Content-Type: application/json" \
  -d "{\"from\":\"$W1\",\"to\":\"$W2\",\"amount_paise\":100,\"idempotency_key\":\"$IDEM_KEY\"}" \
  2>/dev/null || true)
UNIQUE_IDS=$(echo "$RESPONSES" | jq -r '.id // empty' | sort -u | wc -l | tr -d ' ')
ERRORS=$(echo "$RESPONSES" | jq -r 'select(.error != null) | .error' | grep -v INSUFFICIENT | wc -l | tr -d ' ' || true)
ERRORS=${ERRORS:-0}
echo "Unique transfer IDs: $UNIQUE_IDS  |  Unexpected errors: $ERRORS"
if [ "$ERRORS" -eq 0 ] && [ "$UNIQUE_IDS" -le 1 ] && [ "$UNIQUE_IDS" -ge 1 ]; then
  ok "TEST 2: All responses share same transfer ID"
else
  fail "TEST 2: $UNIQUE_IDS IDs and $ERRORS errors — expected 1 ID and 0 errors"
fi

echo ""
echo "--- TEST 3: 50 A→B + 50 B→A — conservation + no negative ---"
BEFORE_W1=$(curl -sf "$BASE_URL/wallets/$W1" -H "Authorization: Bearer $TOKEN_USER1" | jq -r '.balance')
BEFORE_W2=$(curl -sf "$BASE_URL/wallets/$W2" -H "Authorization: Bearer $TOKEN_USER2" | jq -r '.balance')
BEFORE_SUM=$((BEFORE_W1 + BEFORE_W2))
echo "Before: W1=$BEFORE_W1  W2=$BEFORE_W2  sum=$BEFORE_SUM"

SEQ="con-$(date +%s%N)"
(seq 1 50 | xargs -P50 -I{} curl -sf -X POST "$BASE_URL/transfers" \
   -H "Authorization: Bearer $TOKEN_USER1" -H "Content-Type: application/json" \
   -d "{\"from\":\"$W1\",\"to\":\"$W2\",\"amount_paise\":100,\"idempotency_key\":\"${SEQ}-ab-{}\"}" \
   > /dev/null 2>&1 &
 seq 1 50 | xargs -P50 -I{} curl -sf -X POST "$BASE_URL/transfers" \
   -H "Authorization: Bearer $TOKEN_USER1" -H "Content-Type: application/json" \
   -d "{\"from\":\"$W2\",\"to\":\"$W1\",\"amount_paise\":100,\"idempotency_key\":\"${SEQ}-ba-{}\"}" \
   > /dev/null 2>&1 &
 wait)

BAL_W1=$(curl -sf "$BASE_URL/wallets/$W1" -H "Authorization: Bearer $TOKEN_USER1" | jq -r '.balance')
BAL_W2=$(curl -sf "$BASE_URL/wallets/$W2" -H "Authorization: Bearer $TOKEN_USER2" | jq -r '.balance')
AFTER_SUM=$((BAL_W1 + BAL_W2))
echo "After:  W1=$BAL_W1  W2=$BAL_W2  sum=$AFTER_SUM"

if [ "${BAL_W1:-0}" -ge 0 ] && [ "${BAL_W2:-0}" -ge 0 ]; then
  ok "TEST 3a: No negative balances after A↔B contention"
else
  fail "TEST 3a: Negative balance detected (W1=$BAL_W1 W2=$BAL_W2)"
fi

if [ "$AFTER_SUM" -eq "$BEFORE_SUM" ]; then
  ok "TEST 3b: Conservation holds (sum unchanged: $BEFORE_SUM)"
else
  fail "TEST 3b: Conservation broken (before=$BEFORE_SUM after=$AFTER_SUM)"
fi

echo ""
echo "--- Checking /actuator/prometheus domain counters ---"
METRICS=$(curl -sf "$BASE_URL/actuator/prometheus" 2>/dev/null || echo "")
for COUNTER in transfers_created_total transfers_declined_insufficient_funds_total \
               transfers_idempotent_replay_total wallets_created_total; do
  if echo "$METRICS" | grep -q "$COUNTER"; then
    ok "Metric $COUNTER present"
  else
    fail "Metric $COUNTER MISSING"
  fi
done

echo ""
echo "==============================="
echo " Results: ${PASS} passed, ${FAIL} failed"
echo "==============================="
exit "$FAIL"
