#!/usr/bin/env bash
# DEEP FEATURE TEST SUITE (bash port of deep-test.ps1).
#
# PowerShell is not available on every machine this repo is developed on, and an
# evidence gate that cannot run is not evidence. This is the same 20-section
# sweep, ported to bash in the style of console-contract-gate.sh, with every
# expectation re-verified against the actual handlers in JunifyDBServer:
#   - POST /api/sql, /api/tables, /api/constraints are REMOVED SQL-era routes and
#     must answer 404 (the old ps1 still expected tables/constraints to exist).
#   - sismember is GET ?member= (the old ps1 used POST with a body).
#   - sadd/srem take {"members":[...]} (the old ps1 used a singular "member").
#   - bulk is POST /api/bulk/{collection} with a JSON array -> 201.
#   - schema create is POST /api/schema/{name} -> 201.
#   - index create is POST /api/indexes/{collection} {"field":...} -> 201.
#   - vector insert is POST /api/vectors/{index}/{id} -> 201 (create-on-first-use).
#   - hget reads the field from ?field=, not a path segment.
#   - login password is the API key (the old ps1 used admin/admin123).
#
# Usage: scripts/deep-test.sh [port]
# Requires: target/junify-db-core-1.0.0.jar (build with: mvn -DskipTests package)
set -u

PORT="${1:-8099}"
BASE="http://127.0.0.1:${PORT}"
JAR="target/junify-db-core-1.0.0.jar"
DATA_DIR="target/deep-test-data"
LOG="target/deep-test-server.log"
KEY="deep-test-key-DO-NOT-USE-IN-PROD"
BODY_FILE="/tmp/deep-test-body.json"

PASS=0
FAIL=0
DETAILS=()
SERVER_PID=""

fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); DETAILS+=("$1"); }
pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
  fi
  # Git Bash kill cannot always signal a native JVM on Windows; make sure the
  # port is actually released or every later run fails its readiness probe.
  local pid
  pid=$(netstat -ano 2>/dev/null | grep -E "[:.]${PORT}[[:space:]]" | grep -i listen | awk '{print $NF}' | head -1 | tr -d '\r')
  [ -n "$pid" ] && command -v taskkill >/dev/null 2>&1 && taskkill //F //PID "$pid" >/dev/null 2>&1
}
trap cleanup EXIT

# T <label> <method> <path> <expected-code> [body]
T() {
  local label="$1" method="$2" path="$3" want="$4" body="${5:-}"
  local extra=() out
  [ -n "$body" ] && extra=(-H 'Content-Type: application/json' -d "$body")
  out=$(curl -s -m 10 -o "$BODY_FILE" -w '%{http_code}' -X "$method" \
    "$BASE$path" -H "X-API-Key: $KEY" "${extra[@]}")
  if [ "$out" = "$want" ]; then
    pass "$label (HTTP $out)"
    return 0
  fi
  fail "$label -> HTTP $out (expected $want): $(head -c 120 "$BODY_FILE" 2>/dev/null)"
  return 1
}

# TNoAuth <label> <path> <expected-code>
TNoAuth() {
  local label="$1" path="$2" want="$3"
  local out
  out=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE$path")
  if [ "$out" = "$want" ]; then
    pass "$label (HTTP $out, no auth)"
  else
    fail "$label -> HTTP $out without auth (expected $want)"
  fi
}

# first string field of the last response body, e.g. the inserted document id
last_id() { grep -o '"id":"[^"]*"' "$BODY_FILE" | head -1 | sed 's/"id":"//;s/"$//'; }
# transactionId number from a begin response
last_txid() { grep -o '"transactionId":[0-9]*' "$BODY_FILE" | head -1 | grep -o '[0-9]*'; }

[ -f "$JAR" ] || { echo "FAIL: $JAR missing — run: mvn -DskipTests package"; exit 2; }
rm -rf "$DATA_DIR"
if curl -s -m 2 -o /dev/null "$BASE/api/health" 2>/dev/null; then
  echo "FAIL: something is already listening on :$PORT — kill it before running"
  exit 2
fi

echo "=== JunifyDB DEEP FEATURE TEST SUITE (bash) ==="
echo "== booting server WITH --api-key on :$PORT =="
java -jar "$JAR" --port "$PORT" --data-dir "$DATA_DIR" --engine FILE --sync --api-key "$KEY" >"$LOG" 2>&1 &
SERVER_PID=$!
for i in $(seq 1 30); do
  code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$BASE/api/health" 2>/dev/null)
  [ "$code" != "000" ] && [ -n "$code" ] && break
  [ "$i" = 30 ] && { echo "FAIL: server did not come up (see $LOG)"; exit 2; }
  sleep 1
done
echo "server up (pid $SERVER_PID)"

R=$((RANDOM % 9000 + 1000))
COL="test_col_$R"
KV="kv_$R"; LB="lb_$R"; SB="sb_$R"; HB="hb_$R"; CF="cf_$R"
DQ="dq_$R"; VC="vc_$R"

echo
echo "--- 1. HEALTH CHECK ---"
if T "GET /api/health" GET /api/health 200; then
  case "$(cat "$BODY_FILE")" in
    *'"status":"ok"'*) pass "health.status = ok" ;;
    *) fail "health.status != ok: $(head -c 120 "$BODY_FILE")" ;;
  esac
fi

echo
echo "--- 2. AUTHENTICATION ---"
TNoAuth "GET /api/health without key should 401" /api/health 401
T "POST /api/auth/login valid creds" POST /api/auth/login 200 \
  "{\"username\":\"admin\",\"password\":\"$KEY\"}"
T "POST /api/auth/login bad password should 401" POST /api/auth/login 401 \
  '{"username":"admin","password":"wrongpass"}'

echo
echo "--- 3. METRICS AND STATS ---"
T "GET /api/metrics" GET /api/metrics 200
T "GET /api/stats" GET /api/stats 200

echo
echo "--- 4. DOCUMENT COLLECTIONS ---"
T "POST collection insert doc1" POST "/api/collections/$COL" 201 \
  '{"name":"Alice","age":30,"city":"Paris"}'
ID1=$(last_id)
T "POST collection insert doc2" POST "/api/collections/$COL" 201 \
  '{"name":"Bob","age":25,"city":"London"}'
ID2=$(last_id)
T "POST collection insert doc3" POST "/api/collections/$COL" 201 \
  '{"name":"Carol","age":35,"city":"Paris"}'
ID3=$(last_id)
T "GET collection list all" GET "/api/collections/$COL" 200
T "GET collection by id" GET "/api/collections/$COL/$ID1" 200
T "POST collection query eq" POST "/api/collections/$COL/query" 200 '{"city":"Paris"}'
T "POST collection query gt" POST "/api/collections/$COL/query" 200 '{"$gt":{"age":28}}'
T "POST collection query lt" POST "/api/collections/$COL/query" 200 '{"$lt":{"age":32}}'
T "GET collection stats" GET "/api/collections/$COL/stats" 200
T "POST set-ttl on doc" POST "/api/collections/$COL/set-ttl" 200 \
  "{\"documentId\":\"$ID1\",\"ttlSeconds\":3600}"
T "POST cleanup expired" POST "/api/collections/$COL/cleanup" 200 '{}'
T "PUT update doc" PUT "/api/collections/$COL/$ID2" 201 \
  '{"name":"Bob Updated","age":26,"city":"Berlin"}'
T "DELETE doc" DELETE "/api/collections/$COL/$ID3" 204
T "GET deleted doc should 404" GET "/api/collections/$COL/$ID3" 404

echo
echo "--- 5. KEY-VALUE STORE ---"
T "PUT kv key1" PUT "/api/kv/$KV/key1" 201 '{"value":"hello-world"}'
T "GET kv key1" GET "/api/kv/$KV/key1" 200
T "PUT kv key2" PUT "/api/kv/$KV/key2" 201 '{"value":"42"}'
T "DELETE kv key1" DELETE "/api/kv/$KV/key1" 204
T "GET deleted kv key should 404" GET "/api/kv/$KV/key1" 404

echo
echo "--- 6. LIST BUCKET ---"
T "LIST rpush alpha" POST "/api/kv/lists/$LB/mylist/rpush" 200 '{"value":"alpha"}'
T "LIST rpush beta" POST "/api/kv/lists/$LB/mylist/rpush" 200 '{"value":"beta"}'
T "LIST lpush zero" POST "/api/kv/lists/$LB/mylist/lpush" 200 '{"value":"zero"}'
T "LIST lrange" GET "/api/kv/lists/$LB/mylist/lrange" 200
T "LIST llen" GET "/api/kv/lists/$LB/mylist/llen" 200
T "LIST lpop" POST "/api/kv/lists/$LB/mylist/lpop" 200 '{}'
T "LIST rpop" POST "/api/kv/lists/$LB/mylist/rpop" 200 '{}'

echo
echo "--- 7. SET BUCKET ---"
T "SET sadd a" POST "/api/kv/sets/$SB/myset/sadd" 200 '{"members":["a"]}'
T "SET sadd b" POST "/api/kv/sets/$SB/myset/sadd" 200 '{"members":["b"]}'
T "SET sadd c" POST "/api/kv/sets/$SB/myset/sadd" 200 '{"members":["c"]}'
T "SET smembers" GET "/api/kv/sets/$SB/myset/smembers" 200
T "SET sismember b" GET "/api/kv/sets/$SB/myset/sismember?member=b" 200
T "SET scard" GET "/api/kv/sets/$SB/myset/scard" 200
T "SET srem a" POST "/api/kv/sets/$SB/myset/srem" 200 '{"members":["a"]}'

echo
echo "--- 8. HASH BUCKET ---"
T "HASH hset name" POST "/api/kv/hashes/$HB/user1/hset" 200 '{"field":"name","value":"Alice"}'
T "HASH hset age" POST "/api/kv/hashes/$HB/user1/hset" 200 '{"field":"age","value":"30"}'
T "HASH hgetall" GET "/api/kv/hashes/$HB/user1/hgetall" 200
T "HASH hget name" GET "/api/kv/hashes/$HB/user1/hget?field=name" 200
T "HASH hlen" GET "/api/kv/hashes/$HB/user1/hlen" 200
T "HASH hdel age" POST "/api/kv/hashes/$HB/user1/hdel" 200 '{"field":"age"}'

echo
echo "--- 9. COLUMN FAMILY ---"
T "COL PUT row1" PUT "/api/columns/$CF/row1" 201 '{"col1":"val1","col2":"val2"}'
T "COL GET row1" GET "/api/columns/$CF/row1" 200
T "COL PUT row2" PUT "/api/columns/$CF/row2" 201 '{"col1":"x","col2":"y"}'
T "COL GET all" GET "/api/columns/$CF" 200
T "COL DELETE row1" DELETE "/api/columns/$CF/row1" 204

echo
echo "--- 10. DOCUMENT QUERY (SQL routes removed) ---"
T "DOC insert 1" POST "/api/collections/$DQ" 201 '{"id":"d1","name":"Keyboard","stock":41}'
T "DOC insert 2" POST "/api/collections/$DQ" 201 '{"id":"d2","name":"Mouse Pad","stock":7}'
T "DOC query all" POST "/api/collections/$DQ/query" 200 '{}'
T "DOC query eq" POST "/api/collections/$DQ/query" 200 '{"name":{"$eq":"Keyboard"}}'
T "DOC query range" POST "/api/collections/$DQ/query" 200 '{"stock":{"$gt":10}}'
T "DOC query regex" POST "/api/collections/$DQ/query" 200 '{"name":{"$regex":"Key"}}'
T "DOC query and" POST "/api/collections/$DQ/query" 200 \
  '{"$and":[{"name":{"$eq":"Keyboard"}},{"stock":{"$gt":10}}]}'
T "DOC update" PUT "/api/collections/$DQ/d1" 201 '{"id":"d1","name":"Keyboard","stock":39}'
T "DOC delete" DELETE "/api/collections/$DQ/d2" 204
T "SQL endpoint removed" POST "/api/sql" 404 '{"query":"SELECT 1"}'

echo
echo "--- 11. SCHEMA MANAGEMENT ---"
T "SCHEMA list" GET /api/schema 200
T "SCHEMA create" POST "/api/schema/sch_$R" 201 '{"strict":false}'

echo
echo "--- 12. VECTOR SEARCH ---"
T "VEC insert v1" POST "/api/vectors/$VC/v1" 201 \
  '{"vector":[0.1,0.2,0.3,0.4],"metadata":{"label":"A"}}'
T "VEC insert v2" POST "/api/vectors/$VC/v2" 201 \
  '{"vector":[0.9,0.8,0.7,0.6],"metadata":{"label":"B"}}'
T "VEC insert v3" POST "/api/vectors/$VC/v3" 201 \
  '{"vector":[0.1,0.15,0.25,0.35],"metadata":{"label":"C"}}'
T "VEC search knn=2" POST "/api/vectors/$VC/search" 200 \
  '{"vector":[0.1,0.2,0.3,0.4],"k":2}'
T "VEC get by id" GET "/api/vectors/$VC/v1" 200
T "VEC delete v2" DELETE "/api/vectors/$VC/v2" 204

echo
echo "--- 13. BULK OPERATIONS ---"
T "BULK batch insert" POST "/api/bulk/bulk_$R" 201 \
  '[{"name":"X1","val":1},{"name":"X2","val":2},{"name":"X3","val":3}]'

echo
echo "--- 14. TRANSACTIONS ---"
T "TXN begin" POST /api/transactions 200 '{}'
TXID=$(last_txid)
T "TXN status" GET /api/transactions 200
T "TXN commit" POST /api/transactions 200 "{\"action\":\"commit\",\"transactionId\":$TXID}"
T "TXN begin (2nd)" POST /api/transactions 200 '{}'
TXID2=$(last_txid)
T "TXN rollback" POST /api/transactions 200 "{\"action\":\"rollback\",\"transactionId\":$TXID2}"

echo
echo "--- 15. INDEXES ---"
T "IDX list" GET /api/indexes/ 200
T "IDX create" POST "/api/indexes/$COL" 201 '{"field":"name"}'

echo
echo "--- 16. BACKUP ---"
T "BACKUP trigger" POST /api/backup 200 '{}'
T "BACKUP list" GET /api/backup 200

echo
echo "--- 17. RETIRED SQL ROUTES (must 404) ---"
T "TABLES route removed" GET /api/tables/ 404
T "CONSTRAINTS route removed" GET /api/constraints/ 404
T "SQL execute route removed" POST /api/sql/execute 404 '{"query":"SELECT 1"}'

echo
echo "--- 18. AUDIT LOG ---"
T "AUDIT get all" GET /api/audit/logs 200
T "AUDIT limit 10" GET "/api/audit/logs?limit=10" 200
T "AUDIT filter operation" GET "/api/audit/logs?operation=INSERT" 200

echo
echo "--- 19. CDC ---"
T "CDC events" GET /api/cdc 200

echo
echo "--- 20. FRONTEND STATIC FILES (anonymous) ---"
TNoAuth "GET / index.html" / 200
TNoAuth "GET /login.html" /login.html 200
TNoAuth "GET /logo.svg" /logo.svg 200
TNoAuth "GET /favicon.svg" /favicon.svg 200

echo
echo "========================================"
echo "  PASSED : $PASS"
if [ "$FAIL" -gt 0 ]; then
  echo "  FAILED : $FAIL"
else
  echo "  FAILED : 0"
fi
echo "  TOTAL  : $((PASS + FAIL))"
echo "========================================"

if [ "$FAIL" -gt 0 ]; then
  echo
  echo "FAILED DETAILS:"
  for d in "${DETAILS[@]}"; do echo "  FAIL: $d"; done
  echo "DEEP TEST: FAIL"
  exit 1
fi
echo "DEEP TEST: PASS"
exit 0
