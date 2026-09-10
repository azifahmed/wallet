# Wallet Transfer — Quick Ops

Default live base URL (change if yours differs):

```bash
export BASE_URL=https://wallet-transfer-mleu.onrender.com
```

Auth tokens (from `application.yml`): `token-user1` … `token-user5`, `token-admin`.

---

## Health / actuator

No auth required.

```bash
# Liveness
curl -s "$BASE_URL/actuator/health" | jq .

# Prometheus domain counters
curl -s "$BASE_URL/actuator/prometheus" | grep -E 'transfers_|wallets_created'
```

Expect health: `{"status":"UP",...}`.

---

## Create wallet & check balance

```bash
# Get-or-create wallet for user1
W1=$(curl -sf -X POST "$BASE_URL/wallets" \
  -H "Authorization: Bearer token-user1" | jq -r '.walletId')
echo "W1=$W1"

# Read balance (any bearer token)
curl -sf "$BASE_URL/wallets/$W1" \
  -H "Authorization: Bearer token-user1" | jq .
```

Response shape: `{ "walletId", "userId", "balance" }` — `balance` is integer paise.

---

## Admin ops (dev / demo only)

`token-admin` endpoints below are for local/demo reset while iterating on burst tests.
They are **not** production money APIs (mint, wipe, and hard-delete history).

```bash
# Seed / mint (Add Money) — 1000000 paise = ₹10,000
curl -sf -X POST "$BASE_URL/wallets/$W1/credit" \
  -H "Authorization: Bearer token-admin" \
  -H "Content-Type: application/json" \
  -d '{"amount_paise":1000000}' | jq .

# Clear balance → 0 (wallet row kept)
curl -sf -X POST "$BASE_URL/wallets/$W1/clear" \
  -H "Authorization: Bearer token-admin" | jq .

# Delete wallet (also deletes transfers that reference it) — 204 No Content
# After this, POST /wallets with the same user creates a fresh wallet again.
curl -sf -X DELETE "$BASE_URL/wallets/$W1" \
  -H "Authorization: Bearer token-admin" -w "\nHTTP %{http_code}\n"
```

Non-admin callers get `403 FORBIDDEN`.

---

## Burst concurrency test

Needs `curl`, `jq`, and `xargs`. Seeds wallets automatically via admin credit.

```bash
# Against deployed URL (default baked into script if you omit the arg)
bash burst.sh "$BASE_URL"

# Against local compose
docker compose up -d --build
bash burst.sh http://localhost:8080
```

---

## Logs

Structured JSON on stdout (`event=…`, `correlation_id=…`).

- **Render:** Dashboard → your web service → **Logs** (watch while `burst.sh` runs).
- **Local:** `docker compose logs -f app`

Useful domain events: `wallet_credited`, `wallet_balance_cleared`, `wallet_deleted`,
`transfer_debited`, `transfer_credited`, `transfer_completed`,
`transfer_declined_insufficient_funds`, `transfer_idempotent_replay`.
