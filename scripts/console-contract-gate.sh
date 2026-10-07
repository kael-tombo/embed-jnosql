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
# Requires: target/embed-jnosql-core-1.0.0.jar (build with: mvn -DskipTests package)
set -u

PORT="${1:-8097}"
# R-53: ENGINE env var lets CI run the full contract on every storage engine
# (FILE, LSM_TREE, B_TREE). IN_MEMORY is excluded from any restart-dependent
# assertions by the nature of the engine.
ENGINE="${ENGINE:-FILE}"
BASE="http://127.0.0.1:${PORT}"
JAR="target/embed-jnosql-core-1.0.0.jar"
DATA_DIR="target/contract-gate-data-${ENGINE}"
LOG="target/contract-gate-server-${ENGINE}.log"

FAILURES=0
SERVER_PID=""
CORS_PID=""

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
  if [ -n "$CORS_PID" ] && kill -0 "$CORS_PID" 2>/dev/null; then
    kill "$CORS_PID" 2>/dev/null
    wait "$CORS_PID" 2>/dev/null
  fi
  # The restarted server is a new process by the time this runs; leave no orphan holding the port,
  # or the next run fails its stale-server check and the next *engine* cannot be gated at all.
  stop_pid "$(server_pid_for_port)"
}
trap cleanup EXIT

# Is a server answering on $PORT? Ground truth for "the server really stopped": a pid liveness
# check is unreliable here (Git Bash kill does not always terminate a native JVM on Windows),
# while a health answer cannot be faked by a dead process.
server_answers() {
  [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$BASE/api/health" 2>/dev/null)" = "200" ]
}

# The pid listening on $PORT, or empty. Windows-first, with a portable fallback.
server_pid_for_port() {
  local pid=""
  if command -v powershell >/dev/null 2>&1; then
    pid=$(powershell -NoProfile -Command \
      "(Get-NetTCPConnection -LocalPort $PORT -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess)" \
      2>/dev/null | tr -d '\r' | head -1)
  fi
  if [ -z "$pid" ]; then
    pid=$(netstat -ano 2>/dev/null | grep -E "[:.]$PORT[[:space:]]" | grep -i listen | awk '{print $NF}' | head -1 | tr -d '\r')
  fi
  printf '%s' "$pid"
}

# Stop a pid for real on this platform.
#
# Measured on Windows: Git Bash's `kill` cannot signal a native JVM at all — `kill -9 <pid>`
# answers "No such process" for a pid that is demonstrably alive and serving HTTP, and the server
# keeps answering. taskkill does terminate it. So taskkill is tried first where it exists, with
# `kill -9` as the portable fallback (Linux CI).
stop_pid() {
  local pid="$1"
  [ -n "$pid" ] || return 0
  if command -v taskkill >/dev/null 2>&1; then
    taskkill //F //PID "$pid" >/dev/null 2>&1 && return 0
  fi
  kill -9 "$pid" 2>/dev/null
  return 0
}

# stop_server <what>: stop the server and *prove* it stopped, so a "restart" cannot be answered
# by the process it was supposed to replace. Returns non-zero if the port is still serving.
#
# The pid that matters is the one listening on $PORT, not $! : the shell's idea of the job pid and
# the OS pid do not always agree here, and only the listener can answer the checks below.
stop_server() {
  local what="$1"
  stop_pid "$(server_pid_for_port)"
  stop_pid "$SERVER_PID"
  for i in $(seq 1 20); do
    server_answers || { pass "$what server stopped (port $PORT released)"; return 0; }
    stop_pid "$(server_pid_for_port)"
    sleep 1
  done
  fail "$what server is still answering on :$PORT after being stopped — every restart check below would be answered by the original process, so they are not evidence"
  return 1
}

# start_server <suffix>: boot the jar on the same data dir and prove it is the process answering.
# A failed bind leaves the *old* server serving, which would make the checks below meaningless.
start_server() {
  local suffix="$1"
  java -jar "$JAR" --port "$PORT" --data-dir "$DATA_DIR" --engine "$ENGINE" --sync >"$LOG.$suffix" 2>&1 &
  SERVER_PID=$!
  for i in $(seq 1 30); do
    if server_answers; then
      if grep -q "Address already in use" "$LOG.$suffix" 2>/dev/null; then
        fail "server could not bind :$PORT after $suffix (see $LOG.$suffix) — the answers below came from a process that never restarted"
        return 1
      fi
      pass "server restarted for $suffix (pid $SERVER_PID)"
      return 0
    fi
    sleep 1
  done
  fail "server did not come back up after $suffix (see $LOG.$suffix)"
  return 1
}

[ -f "$JAR" ] || { echo "FAIL: $JAR missing — run: mvn -DskipTests package"; exit 2; }
rm -rf "$DATA_DIR"

# A stale server already bound to $PORT would masquerade as the freshly built
# jar and silently invalidate every check below (this actually happened: an
# IN_MEMORY probe server on :8097 made the vector-persistence check fail
# against a server that by design never persists). Fail fast instead.
if curl -s -m 2 -o /dev/null "$BASE/api/health" 2>/dev/null; then
  echo "FAIL: something is already listening on :$PORT — kill it before running the gate"
  exit 2
fi

echo "== booting server on :$PORT ($ENGINE) =="
java -jar "$JAR" --port "$PORT" --data-dir "$DATA_DIR" --engine "$ENGINE" --sync >"$LOG" 2>&1 &
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
echo "== document reads must not create collections or fake success (R-48, R-49) =="
COLS_BEFORE=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)

BODY=$(curl -s -m 5 -X POST "$BASE/api/collections/gate_missing_t/query" -H 'Content-Type: application/json' -d '{"filter":{}}')
CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gate_missing_t/query" -H 'Content-Type: application/json' -d '{"filter":{}}')
if [ "$CODE" = "404" ]; then
  pass "query on an unknown collection -> 404"
else
  fail "query on an unknown collection returned $CODE: $(echo "$BODY" | head -c 100) (want 404)"
fi

COLS_AFTER=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)
if [ "$COLS_AFTER" = "$COLS_BEFORE" ]; then
  pass "failed query created no collection"
else
  fail "failed query mutated the catalog ($COLS_BEFORE -> $COLS_AFTER collections)"
fi

N=$(curl -s -m 5 "$BASE/api/collections" | grep -o 'gate_missing_t' | wc -l)
if [ "$N" = "0" ]; then
  pass "no leftover empty collection from reads"
else
  fail "gate_missing_t exists in the catalog — read-auto-create is back"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/collections/gate_missing_t")
if [ "$CODE" = "404" ]; then
  pass "DELETE on an unknown collection -> 404"
else
  fail "DELETE on an unknown collection returned $CODE (want 404) — it must not create what it was asked to remove"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gateq/query" -H 'Content-Type: application/json' -d '{"id":"q1"}')
if [ "$CODE" = "200" ]; then
  pass "query on an existing collection still works"
else
  fail "query on an existing collection returned $CODE (want 200)"
fi

echo "== collection resolution must never create (R-55) =="
COLS_BEFORE=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' "$BASE/api/collections/gate_ghost_coll")
if [ "$CODE" = "404" ]; then
  pass "GET unknown collection -> 404"
else
  fail "GET on an unknown collection returned $CODE (want 404) — reads are resolving collections into existence"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/collections/gate_ghost_coll")
if [ "$CODE" = "404" ]; then
  pass "DELETE unknown collection -> 404"
else
  fail "DELETE on an unknown collection returned $CODE (want 404) — it must not create what it was asked to remove"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gate_ghost_coll/query" -H 'Content-Type: application/json' -d '{}')
if [ "$CODE" = "404" ]; then
  pass "query on unknown collection -> 404"
else
  fail "query on an unknown collection returned $CODE (want 404)"
fi

COLS_AFTER=$(curl -s -m 5 "$BASE/api/collections" | grep -o '"name"' | wc -l)
if [ "$COLS_BEFORE" = "$COLS_AFTER" ]; then
  pass "failed collection requests left the catalog unchanged ($COLS_BEFORE)"
else
  fail "catalog grew on failed requests ($COLS_BEFORE -> $COLS_AFTER)"
fi

N=$(curl -s -m 5 "$BASE/api/collections" | grep -o 'gate_ghost_coll' | wc -l)
if [ "$N" = "0" ]; then
  pass "no ghost collection left behind"
else
  fail "gate_ghost_coll exists — collection resolution created it"
fi

CODE=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gate_fresh_coll" -H 'Content-Type: application/json' -d '{"id":"g1","v":1}')
if [ "$CODE" = "201" ]; then
  pass "POST still auto-creates (schemaless workflow intact)"
else
  fail "POST to a new collection returned $CODE (want 201)"
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
echo "== vector indexes are persisted next to the data (R-52) =="
curl -s -m 5 -X POST "$BASE/api/vectors/gate_vec/gpersist" -H 'Content-Type: application/json' -d '{"vector":[4,5,6]}' >/dev/null
if ls "$DATA_DIR"/vectors/gate_vec.json >/dev/null 2>&1; then
  pass "vector index written to <dataDir>/vectors/gate_vec.json on mutation"
else
  fail "no vectors/gate_vec.json under $DATA_DIR — restart will lose every vector"
fi

SIZE=$(grep -o 'gpersist' "$DATA_DIR/vectors/gate_vec.json" 2>/dev/null | wc -l)
if [ "$SIZE" -ge 1 ]; then
  pass "persisted JSON contains the added vector id"
else
  fail "persisted JSON missing the added vector id"
fi

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
echo "== R-61: a default server must not advertise wildcard CORS =="
# SecurityConfig documents "secure default: CORS disabled", and the console SPA is
# served from this same origin, so it never needs CORS. A wildcard allow-origin makes
# response bodies readable by any site the operator visits while the server runs.
DEFAULT_ACAO=$(curl -s -m 5 -D - -o /dev/null -H 'Origin: https://evil.example' \
  "$BASE/api/health" | tr -d '\r' | grep -i '^access-control-allow-origin:' || true)
if [ -z "$DEFAULT_ACAO" ]; then
  pass "no Access-Control-Allow-Origin on a data endpoint"
else
  fail "default server advertised '$DEFAULT_ACAO' — the documented default is CORS off"
fi
STREAM_ACAO=$(curl -s -m 5 -D - -o /dev/null -H 'Origin: https://evil.example' \
  "$BASE/api/metrics/stream" 2>/dev/null | tr -d '\r' | grep -i '^access-control-allow-origin:' || true)
if [ -z "$STREAM_ACAO" ]; then
  pass "metrics stream honours the CORS policy"
else
  fail "metrics stream advertised '$STREAM_ACAO' — it must not bypass the policy"
fi

echo
echo "== R-61: CORS opt-in is reachable without an API key =="
# Before R-61 the security config was applied only when auth was enabled, so this
# documented setting did nothing on a no-auth server.
CORS_PORT=$((PORT + 1))
CORS_BASE="http://127.0.0.1:$CORS_PORT"
if curl -s -m 2 -o /dev/null "$CORS_BASE/api/health" 2>/dev/null; then
  fail "something is already listening on :$CORS_PORT — cannot verify the CORS opt-in"
else
  EMBEDJNOSQL_SECURITY_CORS_ENABLED=true EMBEDJNOSQL_SECURITY_ALLOWED_ORIGINS=https://app.example \
    java -jar "$JAR" --port "$CORS_PORT" --data-dir "$DATA_DIR-cors" --engine "$ENGINE" --sync >"$LOG.cors" 2>&1 &
  CORS_PID=$!
  CORS_UP=0
  for i in $(seq 1 30); do
    if [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$CORS_BASE/api/health" 2>/dev/null)" = "200" ]; then
      CORS_UP=1
      break
    fi
    sleep 1
  done
  if [ "$CORS_UP" = "1" ]; then
    OPTIN_ACAO=$(curl -s -m 5 -D - -o /dev/null -H 'Origin: https://app.example' \
      "$CORS_BASE/api/health" | tr -d '\r' | grep -i '^access-control-allow-origin:' | head -1 || true)
    case "$OPTIN_ACAO" in
      *"https://app.example"*) pass "opt-in CORS echoes the configured origin" ;;
      *) fail "opt-in CORS not applied: got '${OPTIN_ACAO:-no header}'" ;;
    esac
  elif grep -q "Address already in use" "$LOG.cors" 2>/dev/null; then
    # Naming the cause matters: a port already held by something else looked exactly like a
    # broken opt-in until the log was opened (it cost a debugging cycle once).
    fail "CORS opt-in server could not bind :$CORS_PORT — that port is already held by another process (see $LOG.cors)"
  else
    fail "CORS opt-in server did not come up (see $LOG.cors)"
  fi
  if [ -n "$CORS_PID" ] && kill -0 "$CORS_PID" 2>/dev/null; then
    kill "$CORS_PID" 2>/dev/null
    wait "$CORS_PID" 2>/dev/null
  fi
fi

echo
echo "== R-62/R-65: what the server writes must survive a restart, once =="
# The stop below is forced (no graceful shutdown available: Windows cannot signal a console JVM,
# and taskkill is the only thing that works there), so this is a *crash* survival check — a
# strictly harder test than a clean restart, and the reason the assertions below are meaningful.
# The defect lived *across* a restart, so this block stops the server and starts it again on the
# same data dir. Pre-fix on LSM_TREE/B_TREE, replayed WAL records were re-applied into a memtable
# that was already represented on disk, so reads returned every flushed document twice.
CAT_BEFORE=$(curl -s -m 5 "$BASE/api/collections")
expect_contains "the seeded collection is listed before restart" "$CAT_BEFORE" '"name":"gateq"'
ROWS_BEFORE=$(curl -s -m 10 -X POST "$BASE/api/collections/products/query" -H 'Content-Type: application/json' \
  -d '{"filter":{}}' | tr -d ' ' | grep -o '"id":' | wc -l | tr -d ' ')
DOCS_BEFORE=$(curl -s -m 5 "$BASE/api/collections/products" | tr -d ' ' | grep -o '"id":' | wc -l | tr -d ' ')

# R-64: resolving a collection on a read is not a write — probe it before the restart
# so the post-restart catalog is also evidence that the typo created nothing.
TYPO_CODE=$(curl -s -m 5 -o /tmp/contract-gate-typo.json -w '%{http_code}' "$BASE/api/indexes/gate_typo")
if [ "$TYPO_CODE" = "404" ]; then
  pass "GET /api/indexes/{unknown} -> 404"
else
  fail "GET /api/indexes/{unknown} -> $TYPO_CODE (expected 404, body: $(head -c 120 /tmp/contract-gate-typo.json))"
fi
CAT_TYPO=$(curl -s -m 5 "$BASE/api/collections")
case "$CAT_TYPO" in
  *gate_typo*) fail "a read added 'gate_typo' to the catalog" ;;
  *) pass "the index read created nothing" ;;
esac

echo
echo "== restarting the server on the same data dir =="
GRACEFUL_RESTART_OK=0
if stop_server "graceful"; then
  if start_server restart; then
    GRACEFUL_RESTART_OK=1
    CAT_AFTER=$(curl -s -m 5 "$BASE/api/collections")
    case "$CAT_AFTER" in
      *'"name":"gateq"'*) pass "the seeded collection is still in the catalog after a restart" ;;
      *) fail "the seeded collection vanished across the restart: $CAT_AFTER" ;;
    esac
    GHOST_AFTER=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/gate_missing_t/query" \
      -H 'Content-Type: application/json' -d '{"filter":{}}')
    if [ "$GHOST_AFTER" = "404" ]; then
      pass "the unknown collection is still unknown after the restart (reads never materialise)"
    else
      fail "the unknown collection answered $GHOST_AFTER after the restart (want 404) — a read created it"
    fi
    ROWS_AFTER=$(curl -s -m 10 -X POST "$BASE/api/collections/products/query" -H 'Content-Type: application/json' \
      -d '{"filter":{}}' | tr -d ' ' | grep -o '"id":' | wc -l | tr -d ' ')
    if [ -n "$ROWS_BEFORE" ] && [ "$ROWS_BEFORE" = "$ROWS_AFTER" ]; then
      pass "the document query returns the same count after a restart ($ROWS_AFTER)"
    else
      fail "the document query returned $ROWS_AFTER after the restart but $ROWS_BEFORE before it — documents must survive exactly once"
    fi
    DOCS_AFTER=$(curl -s -m 5 "$BASE/api/collections/products" | tr -d ' ' | grep -o '"id":' | wc -l | tr -d ' ')
    if [ "$DOCS_BEFORE" = "$DOCS_AFTER" ]; then
      pass "the documents endpoint agrees with the query after a restart ($DOCS_AFTER documents)"
    else
      fail "documents endpoint returned $DOCS_AFTER documents after the restart but $DOCS_BEFORE before it"
    fi
  fi
fi

echo
echo "== R-66: a killed server still holds every write it acknowledged, even after rotation =="
# The WAL rotates at 1 MB. Twelve ~256 KB documents push ~3 MB through it, so the log has
# rotated before the server is killed outright (kill -9: no flush, no graceful close, nothing
# drained). The log is then the only copy of those writes, which is exactly the state the
# pre-fix code lost: rotation closed the writer and reopened it from a background task, so every
# write in that window was dropped, and recovery read wal.log alone and could not see the
# rotated segments at all.
head -c 262144 /dev/zero | tr '\0' 'x' > /tmp/gate-wal-pad.txt
WAL_ACKED=0
for i in $(seq 1 12); do
  { printf '{"id":"waldoc-%s","pad":"' "$i"; cat /tmp/gate-wal-pad.txt; printf '"}'; } > /tmp/gate-wal-doc.json
  CODE=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/collections/waldocs" \
    -H 'Content-Type: application/json' -d @/tmp/gate-wal-doc.json)
  if [ "$CODE" -ge 200 ] 2>/dev/null && [ "$CODE" -lt 400 ] 2>/dev/null; then
    WAL_ACKED=$((WAL_ACKED + 1))
  else
    fail "document waldoc-$i was not accepted -> HTTP $CODE"
  fi
done
# Not every engine has a WAL: B_TREE persists through its own index, so there is nothing to
# rotate and the replay assertion below cannot apply to it. Claiming a WAL check there would be
# the same kind of false pass this gate exists to prevent, so the engine is asked first.
WAL_USED=0
[ -f "$DATA_DIR/.wal/wal.log" ] && WAL_USED=1
if [ "$WAL_USED" = "1" ]; then
  ARCHIVED=$(ls "$DATA_DIR/.wal/archive"/*.log.gz 2>/dev/null | wc -l | tr -d ' ')
  ROTATED=$(ls "$DATA_DIR"/.wal/wal-*.log 2>/dev/null | wc -l | tr -d ' ')
  if [ "$WAL_ACKED" -gt 0 ] && [ $((ARCHIVED + ROTATED)) -gt 0 ]; then
    pass "the WAL rotated while writing ($ARCHIVED archived, $ROTATED awaiting compression)"
  else
    fail "the WAL never rotated ($ARCHIVED archived, $ROTATED rotated) — this check would not test R-66"
  fi
else
  pass "$ENGINE keeps no write-ahead log, so rotation does not apply — only the survival checks below"
fi

if stop_server "kill -9"; then
  # An unclean kill leaves the WAL as the only copy of the writes above, so this only counts as
  # evidence if the server really is a new process reading that WAL.
  if start_server crash; then
    if [ "$WAL_USED" = "1" ]; then
      RECOVERED_OPS=$(grep -o "recovered [0-9]* operations from WAL" "$LOG.crash" | head -1)
      if [ -n "$RECOVERED_OPS" ]; then
        pass "the restarted server replayed its WAL ($RECOVERED_OPS)"
      else
        fail "the restarted server reported no WAL replay — the documents below would have to come from somewhere else"
      fi
    fi
    WAL_AFTER=$(curl -s -m 20 "$BASE/api/collections/waldocs" | grep -o '"id":"waldoc-[0-9]*"' | sort -u | wc -l | tr -d ' ')
    if [ "$WAL_AFTER" = "$WAL_ACKED" ]; then
      pass "all $WAL_AFTER acknowledged documents survived the kill"
    else
      fail "$WAL_ACKED documents were acknowledged but $WAL_AFTER survived the kill — the WAL dropped writes"
    fi
    # A count alone would not notice a document that came back with its body truncated.
    PAD_OK=0
    PAD_BAD=0
    for i in $(seq 1 "$WAL_ACKED"); do
      LEN=$(curl -s -m 20 "$BASE/api/collections/waldocs/waldoc-$i" | grep -o '"pad":"[^"]*"' | head -1 | wc -c | tr -d ' ')
      if [ "$LEN" -gt 262000 ]; then PAD_OK=$((PAD_OK + 1)); else PAD_BAD=$((PAD_BAD + 1)); fi
    done
    if [ "$PAD_BAD" = "0" ]; then
      pass "every recovered document kept its full body ($PAD_OK checked)"
    else
      fail "$PAD_BAD of $((PAD_OK + PAD_BAD)) recovered documents came back truncated"
    fi
  fi
fi

echo
echo "== the SHIPPED console keeps the collection picker truthful =="
# The picker chips are the panel's source of truth for "which collections exist".
# Round-2 live review found the picker going stale after same-panel mutations
# (a first insert kept showing "No collections yet" until re-entry or ⟳) — a
# JS regression no HTTP check can see. Assert the wiring inside the artifact the
# user actually downloads, not just the working tree.
if ! command -v unzip >/dev/null 2>&1; then
  fail "unzip not available — cannot inspect the shipped static/js/console.js"
else
  JS=$(unzip -p "$JAR" static/js/console.js 2>/dev/null)
  fn_body() { echo "$JS" | awk "/^(async )?function $1\\(/,/^}/"; }
  for f in insertDoc dropDocs cleanupExpired refreshCollections; do
    case "$(fn_body "$f")" in
      *loadColPicker*) pass "console.js $f() refreshes the collection picker" ;;
      *) fail "console.js $f() no longer refreshes the collection picker (stale-chip regression)" ;;
    esac
  done
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
