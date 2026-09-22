#!/usr/bin/env bash
# Console contract gate (R-34..R-41 follow-up).
#
# Boots the real server from the built jar and fails on any of:
#   - a non-2xx/3xx response from a path the console UI calls, or
#   - a dropped connection (no HTTP status line at all), or
#   - a response body that contradicts the documented contract.
#
# Rationale: three rounds of live probing found endpoints that reported success
# while doing nothing (empty backups, starved CDC connectors, a "Subscribers"
# KPI that mirrored the event log) or worse, destroyed data (the index route
# that called clear()). Every one of those returned HTTP 200. A code green
# suite does not catch that class; a contract probe does.
#
# Usage: scripts/console-contract-gate.sh [port]
# Requires: target/junify-db-core-1.0.0.jar (build with: mvn -DskipTests package)
set -u

PORT="${1:-8097}"
BASE="http://127.0.0.1:${PORT}"
JAR="target/junify-db-core-1.0.0.jar"
DATA_DIR="target/contract-gate-data"
LOG="target/contract-gate-server.log"

FAILURES=0
SERVER_PID=""

fail() {
  echo "FAIL: $1"
  FAILURES=$((FAILURES + 1))
}

pass() {
  echo "ok:   $1"
}

# probe <label> <method> <path> [body]
# Fails if the connection drops or the status is outside 2xx/3xx.
probe() {
  local label="$1" method="$2" path="$3" body="${4:-}"
  local extra=()
  [ -n "$body" ] && extra=(-H 'Content-Type: application/json' -d "$body")
  local out code
  out=$(curl -s -m 10 -o /tmp/contract-gate-body.json -w '%{http_code}' \
    -X "$method" "$BASE$path" "${extra[@]}")
  code=$?
  if [ $code -ne 0 ]; then
    fail "$label: connection dropped (curl exit $code, no status line)"
    return
  fi
  if [ "$out" -ge 200 ] 2>/dev/null && [ "$out" -lt 400 ] 2>/dev/null; then
    pass "$label -> $out"
  else
    fail "$label -> unexpected HTTP $out: $(head -c 120 /tmp/contract-gate-body.json)"
  fi
}

# expect <label> <actual> <needle>
expect_contains() {
  local label="$1" body="$2" needle="$3"
  case "$body" in
    *"$needle"*) pass "$label contains '$needle'" ;;
    *) fail "$label: expected '$needle' in: $(echo "$body" | head -c 160)" ;;
  esac
}

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
  fi
}
trap cleanup EXIT

[ -f "$JAR" ] || { echo "FAIL: $JAR missing — run: mvn -DskipTests package"; exit 2; }
rm -rf "$DATA_DIR"

echo "== booting server on :$PORT =="
java -jar "$JAR" --port "$PORT" --data-dir "$DATA_DIR" --engine FILE --sync >"$LOG" 2>&1 &
SERVER_PID=$!

for i in $(seq 1 30); do
  [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$BASE/api/health" 2>/dev/null)" = "200" ] && break
  [ "$i" = 30 ] && { echo "FAIL: server did not come up (see $LOG)"; exit 2; }
  sleep 1
done
echo "server up (pid $SERVER_PID)"

echo
echo "== seed the data the console panels read =="
probe "POST /api/collections/products" POST /api/collections/products \
  '{"id":"p1","name":"Keyboard","price":75.0,"stock":5}'
BODY=$(curl -s -m 5 "$BASE/api/collections/products")
expect_contains "collections/products" "$BODY" '"Keyboard"'

# The schema and column-family panels fetch per-collection resources; a 404 for a
# collection that has none is *honest*, so seed both before probing the GETs.
probe "POST /api/schema/products" POST /api/schema/products \
  '{"strict":false,"fields":[{"name":"name","type":"string","required":true}]}'
probe "PUT /api/columns/fam/row1" PUT /api/columns/fam/row1 '{"name":"Keyboard","qty":5}'
probe "POST /api/indexes/products" POST /api/indexes/products '{"field":"name"}'
probe "POST /api/kv/lists/cart/q/rpush" POST /api/kv/lists/cart/q/rpush '{"value":"item-1"}'
probe "POST /api/kv/sets/tags/t/sadd" POST /api/kv/sets/tags/t/sadd '{"members":["a"]}'
probe "POST /api/kv/hashes/h/k/hset" POST /api/kv/hashes/h/k/hset '{"field":"email","value":"a@b.c"}'

echo
echo "== every GET the console UI makes =="
for p in /api/health /api/metrics /api/stats /api/collections /api/schema \
         /api/transactions /api/cdc /api/cdc/events /api/audit/logs /api/backup \
         /api/collections/products /api/collections/products/p1 \
         /api/indexes/products /api/schema/products \
         /api/kv/lists/cart/q/lrange /api/kv/sets/tags/t/smembers \
         /api/kv/hashes/h/k/hgetall /api/columns/fam/row1; do
  probe "GET $p" GET "$p"
done

echo
echo "== contract checks on responses that previously lied =="
BODY=$(curl -s -m 5 "$BASE/api/cdc")
expect_contains "cdc status" "$BODY" '"subscribers":0'

BODY=$(curl -s -m 5 -X POST "$BASE/api/backup" -H 'Content-Type: application/json' -d '{}')
expect_contains "backup create" "$BODY" '"status":"backup created"'
DOCS=$(echo "$BODY" | grep -o '"documents":[0-9]*' | cut -d: -f2)
if [ -n "$DOCS" ] && [ "$DOCS" -ge 1 ] 2>/dev/null; then
  pass "backup reports $DOCS captured document(s)"
else
  fail "backup response must state how many documents it captured, got: $(echo "$BODY" | head -c 160)"
fi
SNAP=$(echo "$BODY" | grep -o '"file":"[^"]*"' | head -1 | cut -d'"' -f4)
SIZE=$(echo "$BODY" | grep -o '"size":[0-9]*' | cut -d: -f2)
if [ -n "$SIZE" ] && [ "$SIZE" -gt 100 ]; then
  pass "backup size $SIZE > 100 bytes (an empty snapshot would be 22)"
else
  fail "backup size suspiciously small: '$SIZE'"
fi
if [ -n "$SNAP" ]; then
  CONTENT=$(gzip -dc "$SNAP" 2>/dev/null | head -c 400)
  expect_contains "snapshot content" "$CONTENT" "Keyboard"
else
  fail "backup response did not include a file path"
fi

echo
echo "== destructive routes must be safe =="
BODY=$(curl -s -m 5 -X DELETE "$BASE/api/indexes/products")
expect_contains "index delete without field is a 400" "$BODY" 'Specify the index to drop'
DOCS=$(curl -s -m 5 "$BASE/api/collections/products" | grep -o '"id"' | wc -l)
if [ "$DOCS" -ge 1 ]; then
  pass "documents survive index maintenance ($DOCS docs)"
else
  fail "index route deleted documents ($DOCS docs left)"
fi

BODY=$(curl -s -m 5 -X DELETE "$BASE/api/cdc/connectors/never-existed")
expect_contains "unknown connector delete" "$BODY" 'No connector named'

echo
echo "== transactions must not report success for nothing (R-42) =="
BODY=$(curl -s -m 5 -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d '{"action":"commit","transactionId":999999}')
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d '{"action":"commit","transactionId":999999}')
if [ "$CODE" = "404" ]; then
  pass "commit of unknown txId -> 404"
else
  fail "commit of unknown txId returned $CODE (a no-op commit must not say committed)"
fi
TX=$(curl -s -m 5 -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d '{}')
TXID=$(echo "$TX" | tr ',' '\n' | grep transactionId | cut -d: -f2)
curl -s -m 5 -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d "{\"action\":\"commit\",\"transactionId\":$TXID}" >/dev/null
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d "{\"action\":\"commit\",\"transactionId\":$TXID}")
if [ "$CODE" = "404" ]; then
  pass "double commit -> 404 (no second fake success)"
else
  fail "double commit returned $CODE"
fi
ROLL=$(curl -s -m 5 -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d "{}" )
TXID2=$(echo "$ROLL" | tr ',' '\n' | grep transactionId | cut -d: -f2)
BODY=$(curl -s -m 5 -X POST "$BASE/api/transactions" -H 'Content-Type: application/json' -d "{\"action\":\"rollback\",\"transactionId\":$TXID2}")
expect_contains "rollback status" "$BODY" 'rolled_back'
case "$BODY" in
  *rollbackted*) fail "the 'rollbackted' typo is back" ;;
  *) pass "no 'rollbackted' typo" ;;
esac

echo
echo "== bulk inserts must honour client ids (R-44) =="
BODY=$(curl -s -m 5 -X POST "$BASE/api/bulk/gatebulk" -H 'Content-Type: application/json' -d '[{"id":"k1","name":"One"},{"id":"k2","name":"Two"}]')
expect_contains "bulk insert" "$BODY" '"inserted":2'
CODE=$(curl -s -m 5 -o /tmp/contract-gate-body.json -w '%{http_code}' "$BASE/api/bulk/gatebulk")
CODE=$(curl -s -m 5 -o /tmp/contract-gate-body.json -w '%{http_code}' "$BASE/api/collections/gatebulk/k1")
if [ "$CODE" = "200" ]; then
  pass "bulk-inserted id k1 is addressable"
else
  fail "GET /gatebulk/k1 returned $CODE — bulk must honour client ids like the single-doc POST"
fi

echo
echo "== nosql query operators must match and combine (R-45, R-46) =="
curl -s -m 5 -X POST "$BASE/api/collections/gateq" -H 'Content-Type: application/json' -d '{"id":"q1","name":"Keyboard","stock":41}' >/dev/null
curl -s -m 5 -X POST "$BASE/api/collections/gateq" -H 'Content-Type: application/json' -d '{"id":"q2","name":"Mouse Pad","stock":7}' >/dev/null

N=$(curl -s -m 5 -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"name":{"$regex":"Key"}}' | grep -o '"id"' | wc -l)
if [ "$N" = "1" ]; then
  pass "regex 'Key' matches Keyboard (substring semantics)"
else
  fail "regex 'Key' matched $N docs (want 1) — String.matches anchoring is back"
fi

N=$(curl -s -m 5 -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"$and":[{"name":{"$eq":"Keyboard"}},{"stock":{"$gt":10}}]}' | grep -o '"id"' | wc -l)
if [ "$N" = "1" ]; then
  pass "top-level \$and returns the conjunction"
else
  fail "top-level \$and matched $N docs (want 1) — unreachable \$and is back"
fi

N=$(curl -s -m 5 -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"$or":[{"name":"Keyboard"},{"name":"Mouse Pad"}]}' | grep -o '"id"' | wc -l)
if [ "$N" = "2" ]; then
  pass "top-level \$or returns the union"
else
  fail "top-level \$or matched $N docs (want 2)"
fi

echo
echo "== malformed queries are 400, never silent all-docs or 500 (R-47) =="
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"name":{"$gteX":1}}')
if [ "$CODE" = "400" ]; then
  pass "typo'd operator refused with 400"
else
  fail "typo'd operator returned $CODE (want 400)"
fi

N=$(curl -s -m 5 -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"name":{"$gteX":1}}' | grep -o '"id"' | wc -l)
if [ "$N" = "0" ]; then
  pass "typo'd operator returns no rows"
else
  fail "typo'd operator returned $N rows — silent-ignore is back"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"name":{"$regex":"[unclosed"}}')
if [ "$CODE" = "400" ]; then
  pass "invalid regex pattern refused with 400"
else
  fail "invalid regex pattern returned $CODE (want 400)"
fi

echo
echo "== sql reads must not create tables or fake success (R-48, R-49) =="
COLS_BEFORE=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)

BODY=$(curl -s -m 5 -X POST "$BASE/api/sql" -H 'Content-Type: application/json' -d '{"query":"SELECT * FROM gate_missing_t"}')
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/sql" -H 'Content-Type: application/json' -d '{"query":"SELECT * FROM gate_missing_t"}')
if [ "$CODE" = "404" ]; then
  pass "SELECT from unknown table -> 404"
else
  fail "SELECT from unknown table returned $CODE: $(echo "$BODY" | head -c 100) (want 404)"
fi

COLS_AFTER=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)
if [ "$COLS_AFTER" = "$COLS_BEFORE" ]; then
  pass "failed SELECT created no collection"
else
  fail "failed SELECT mutated the catalog ($COLS_BEFORE -> $COLS_AFTER collections)"
fi

N=$(curl -s -m 5 "$BASE/api/collections" | grep -o 'gate_missing_t' | wc -l)
if [ "$N" = "0" ]; then
  pass "no leftover empty collection from SQL reads"
else
  fail "gate_missing_t exists in the catalog — read-auto-create is back"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/sql" -H 'Content-Type: application/json' -d '{"query":"DROP TABLE gate_missing_t"}')
if [ "$CODE" = "404" ]; then
  pass "DROP TABLE unknown -> 404"
else
  fail "DROP TABLE on unknown table returned $CODE (want 404) — fake success is back"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/sql" -H 'Content-Type: application/json' -d '{"query":"SELECT id FROM gateq WHERE id = ''q1''"}')
if [ "$CODE" = "200" ]; then
  pass "SELECT on an existing table still works"
else
  fail "SELECT on existing table returned $CODE (want 200)"
fi

echo
echo "== vector search must not create indexes or fake success (R-50) =="
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/vectors/gate_typo/search" -H 'Content-Type: application/json' -d '{"vector":[1,2,3],"k":3}')
if [ "$CODE" = "404" ]; then
  pass "search on unknown vector index -> 404"
else
  fail "search on unknown vector index returned $CODE (want 404) — silent-create is back"
fi

INFO=$(curl -s -m 5 "$BASE/api/vectors/gate_typo/_")
case "$INFO" in
  *"not found"*) pass "failed search left no empty index behind" ;;
  *) fail "gate_typo now exists — search created it: $INFO" ;;
esac

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/vectors/gate_typo/search" -H 'Content-Type: application/json' -d '{"k":3}')
if [ "$CODE" = "404" ]; then
  pass "unknown-index search 404s before input validation"
else
  fail "unknown-index search with bad body returned $CODE (want 404)"
fi

curl -s -m 5 -X POST "$BASE/api/vectors/gate_vec/v1" -H 'Content-Type: application/json' -d '{"vector":[1,2,3]}' >/dev/null
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/vectors/gate_vec/search" -H 'Content-Type: application/json' -d '{"k":3}')
if [ "$CODE" = "400" ]; then
  pass "missing vector field -> 400 (was NPE 500)"
else
  fail "missing vector field returned $CODE (want 400)"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/vectors/gate_vec/search" -H 'Content-Type: application/json' -d '{"vector":[1,2,3],"k":-5}')
if [ "$CODE" = "400" ]; then
  pass "negative k -> 400 (was bare-number 500)"
else
  fail "negative k returned $CODE (want 400)"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/vectors/gate_vec/search" -H 'Content-Type: application/json' -d '{"vector":[1,2],"k":3}')
if [ "$CODE" = "400" ]; then
  pass "dimension mismatch -> 400 (was 500)"
else
  fail "dimension mismatch returned $CODE (want 400)"
fi

R=$(curl -s -m 5 -X POST "$BASE/api/vectors/gate_vec/search" -H 'Content-Type: application/json' -d '{"vector":[1,2,3],"k":3}')
case "$R" in
  *'"results"'*) pass "real search on a real index works" ;;
  *) fail "real search broken: $R" ;;
esac

echo
echo "== ttl: expired documents read as absent (R-51) =="
curl -s -m 5 -X POST "$BASE/api/collections/gate_ttl" -H 'Content-Type: application/json' -d '{"id":"shortlived","v":1}' >/dev/null
curl -s -m 5 -X POST "$BASE/api/collections/gate_ttl/set-ttl" -H 'Content-Type: application/json' -d '{"documentId":"shortlived","ttlSeconds":1}' >/dev/null
sleep 2

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' "$BASE/api/collections/gate_ttl/shortlived")
if [ "$CODE" = "404" ]; then
  pass "expired document point-read -> 404"
else
  fail "expired document point-read returned $CODE (want 404) — fake-expired:true body is back"
fi

N=$(curl -s -m 5 "$BASE/api/collections/gate_ttl" | grep -o '"id":"shortlived"' | wc -l)
if [ "$N" = "0" ]; then
  pass "expired document hidden from collection scans"
else
  fail "expired document still visible in scans ($N hits)"
fi

D=$(curl -s -m 5 -X POST "$BASE/api/collections/gate_ttl/cleanup")
expect_contains "cleanup sweep reports the physical delete" "$D" '"deleted":1'

echo
echo "== a body-less POST must never drop the connection =="
CODE=$(curl -s -m 10 -o /tmp/contract-gate-body.json -w '%{http_code}' -X POST "$BASE/api/backup")
CURL_EXIT=$?
if [ $CURL_EXIT -ne 0 ]; then
  fail "body-less POST /api/backup dropped the connection"
elif [ "$CODE" -ge 200 ] && [ "$CODE" -lt 400 ]; then
  pass "body-less POST /api/backup -> $CODE"
else
  fail "body-less POST /api/backup -> $CODE"
fi

echo
echo "== restore round trip =="
BEFORE=$(curl -s -m 5 "$BASE/api/collections/products/p1")
curl -s -m 5 -X DELETE "$BASE/api/collections/products/p1" >/dev/null
AFTER_DELETE=$(curl -s -m 5 "$BASE/api/collections/products/p1")
case "$AFTER_DELETE" in
  *Not*found*) pass "p1 deleted" ;;
  *) fail "p1 was not deleted before restore: $(echo "$AFTER_DELETE" | head -c 120)" ;;
esac
if [ -n "$SNAP" ]; then
  probe "POST /api/backup/restore" POST /api/backup/restore "{\"backupFile\":\"$(echo "$SNAP" | sed 's/\\\\/\//g')\"}"
  AFTER_RESTORE=$(curl -s -m 5 "$BASE/api/collections/products/p1")
  case "$AFTER_RESTORE" in
    *Keyboard*) pass "p1 restored with payload" ;;
    *) fail "p1 missing after restore: $(echo "$AFTER_RESTORE" | head -c 120)" ;;
  esac
fi

echo
echo "========================================"
if [ "$FAILURES" -eq 0 ]; then
  echo "CONTRACT GATE: PASS (all console-called endpoints answered honestly)"
  exit 0
else
  echo "CONTRACT GATE: FAIL ($FAILURES failure(s))"
  exit 1
fi
