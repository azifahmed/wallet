#!/usr/bin/env bash
# ============================================================
# Wallet Transfer — Burst Concurrency Test
# Usage: bash burst.sh <BASE_URL>
# ============================================================
set -euo pipefail
BASE_URL="${1:-http://localhost:8080}"
PASS=0; FAIL=0
GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }

echo "==============================="
echo " Wallet Burst Tests: $BASE_URL"
echo "==============================="

echo ""
echo "--- Bootstrap ---"
W1=$(curl -sf -X POST "$BASE_URL/wallets" -H "Authorization: Bearer token-user1" | jq -r '.walletId')
W2=$(curl -sf -X POST "$BASE_URL/wallets" -H "Authorization: Bearer token-user2" | jq -r '.walletId')
echo "W1=$W1  W2=$W2"
echo "Fund wallets before TEST 2/3, e.g.:"
echo "  docker compose exec postgres psql -U walletuser -d walletdb -c \"UPDATE wallets SET balance=1000000 WHERE id IN ('$W1','$W2');\""

echo ""
echo "--- TEST 1: 50 concurrent POST /wallets for same user ---"
WALLET_IDS=$(seq 1 50 | xargs -P50 -I{} \
  curl -sf -X POST "$BASE_URL/wallets" -H "Authorization: Bearer token-user5" 2>/dev/null \
  | jq -r '.walletId' | sort -u | wc -l | tr -d ' ')
if [ "$WALLET_IDS" -eq 1 ]; then
  ok "TEST 1: Exactly 1 wallet ID across 50 concurrent creates"
else
  fail "TEST 1: Got $WALLET_IDS distinct wallet IDs — expected 1"
fi

echo ""
echo "--- TEST 2: 30 concurrent POSTs with same idempotency_key ---"
IDEM_KEY="idem-$(date +%s%N)"
RESPONSES=$(seq 1 30 | xargs -P30 -I{} \
  curl -sf -X POST "$BASE_URL/transfers" \
  -H "Authorization: Bearer token-user1" \
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
echo "--- TEST 3: 50 A→B + 50 B→A concurrent transfers ---"
SEQ="con-$(date +%s%N)"
(seq 1 50 | xargs -P50 -I{} curl -sf -X POST "$BASE_URL/transfers" \
   -H "Authorization: Bearer token-user1" -H "Content-Type: application/json" \
   -d "{\"from\":\"$W1\",\"to\":\"$W2\",\"amount_paise\":100,\"idempotency_key\":\"${SEQ}-ab-{}\"}" \
   > /dev/null 2>&1 &
 seq 1 50 | xargs -P50 -I{} curl -sf -X POST "$BASE_URL/transfers" \
   -H "Authorization: Bearer token-user1" -H "Content-Type: application/json" \
   -d "{\"from\":\"$W2\",\"to\":\"$W1\",\"amount_paise\":100,\"idempotency_key\":\"${SEQ}-ba-{}\"}" \
   > /dev/null 2>&1 &
 wait)
BAL_W1=$(curl -sf "$BASE_URL/wallets/$W1" -H "Authorization: Bearer token-user1" | jq -r '.balance')
BAL_W2=$(curl -sf "$BASE_URL/wallets/$W2" -H "Authorization: Bearer token-user2" | jq -r '.balance')
echo "W1 balance: $BAL_W1  |  W2 balance: $BAL_W2"
if [ "${BAL_W1:-0}" -ge 0 ] && [ "${BAL_W2:-0}" -ge 0 ]; then
  ok "TEST 3: No negative balances after A↔B contention"
else
  fail "TEST 3: Negative balance detected"
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
