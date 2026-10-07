#!/usr/bin/env bash
# Authenticated console contract gate (follow-up to console-contract-gate.sh).
#
# The unauthenticated gate proves every console-called path answers honestly; this
# one proves the security model around them actually enforces:
#   1. With --api-key set, EVERY /api path requires the key (401 without it) —
#      a single unprotected path is a breach.
#   2. A wrong key is rejected everywhere (not just on one handler).
#   3. The key works on every path (a key that authenticates nowhere is a lockout).
#   4. POST /api/auth/login with the key issues a session cookie + CSRF token;
#      logout invalidates the session (401 afterwards).
#
# Usage: scripts/console-auth-gate.sh [port]
# Requires: target/junify-db-core-1.0.0.jar (build with: mvn -DskipTests package)
set -u

PORT="${1:-8098}"
BASE="http://127.0.0.1:${PORT}"
JAR="target/junify-db-core-1.0.0.jar"
DATA_DIR="target/contract-auth-gate-data"
LOG="target/contract-auth-gate-server.log"
KEY="gate-secret-key-DO-NOT-USE-IN-PROD"

FAILURES=0
SERVER_PID=""

fail() { echo "FAIL: $1"; FAILURES=$((FAILURES + 1)); }
pass() { echo "ok:   $1"; }

# status <method> <path> [curl extra args...] -> echoes "code" ; sets BODY
status() {
  local method="$1" path="$2"; shift 2
  BODY=$(curl -s -m 10 -o /tmp/auth-gate-body.json -w '%{http_code}' -X "$method" "$BASE$path" "$@")
  echo "$BODY"
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

echo "== booting server WITH --api-key on :$PORT =="
java -jar "$JAR" --port "$PORT" --data-dir "$DATA_DIR" --engine FILE --sync --api-key "$KEY" >"$LOG" 2>&1 &
SERVER_PID=$!

for i in $(seq 1 30); do
  # Health requires auth too, so readiness here means ANY http answer.
  code=$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$BASE/api/health" 2>/dev/null)
  [ "$code" != "000" ] && [ -n "$code" ] && break
  [ "$i" = 30 ] && { echo "FAIL: server did not come up (see $LOG)"; exit 2; }
  sleep 1
done
echo "server up (pid $SERVER_PID)"

echo
echo "== 1. every console-called path must reject an anonymous request =="
for p in /api/health /api/metrics /api/stats /api/collections /api/schema \
         /api/transactions /api/cdc /api/cdc/events /api/audit/logs /api/backup \
         /api/collections/products /api/indexes/products /api/kv/sessions; do
  CODE=$(status GET "$p")
  if [ "$CODE" = "401" ]; then
    pass "GET $p -> 401 (anonymous)"
  else
    fail "GET $p anonymously returned $CODE (expected 401)"
  fi
done
CODE=$(status POST "/api/collections/products" -H 'Content-Type: application/json' -d '{"id":"x","name":"x"}')
if [ "$CODE" = "401" ]; then
  pass "POST /api/collections/products -> 401 (anonymous)"
else
  fail "anonymous POST returned $CODE (expected 401) — write paths must not be open"
fi

echo
echo "== 2. a WRONG key must be rejected everywhere =="
for p in /api/health /api/collections /api/backup /api/cdc; do
  CODE=$(status GET "$p" -H "X-API-Key: wrong-key-value")
  if [ "$CODE" = "401" ]; then
    pass "GET $p -> 401 (wrong key)"
  else
    fail "GET $p with wrong key returned $CODE (expected 401)"
  fi
done

echo
echo "== 3. the REAL key must work everywhere =="
for p in /api/health /api/metrics /api/stats /api/collections /api/schema \
         /api/transactions /api/cdc /api/cdc/events /api/audit/logs /api/backup; do
  CODE=$(status GET "$p" -H "X-API-Key: $KEY")
  if [ "$CODE" -ge 200 ] 2>/dev/null && [ "$CODE" -lt 300 ] 2>/dev/null; then
    pass "GET $p -> $CODE (authenticated)"
  else
    fail "GET $p with real key returned $CODE"
  fi
done

echo
echo "== 4. authenticated mutations work and persist =="
CODE=$(status POST "/api/collections/products" -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"id":"p1","name":"Keyboard","price":75.0}')
if [ "$CODE" = "201" ] || [ "$CODE" = "200" ]; then
  pass "POST /api/collections/products -> $CODE"
else
  fail "authenticated POST returned $CODE: $(head -c 120 /tmp/auth-gate-body.json)"
fi
BODY=$(curl -s -m 5 "$BASE/api/collections/products/p1" -H "X-API-Key: $KEY")
case "$BODY" in
  *Keyboard*) pass "created document is readable" ;;
  *) fail "document not readable after authenticated create: $(echo "$BODY" | head -c 120)" ;;
esac

echo
echo "== 5. login / logout session lifecycle =="
LOGIN_HEADERS="/tmp/auth-gate-login-headers.txt"
CODE=$(curl -s -m 10 -o /tmp/auth-gate-body.json -D "$LOGIN_HEADERS" -w '%{http_code}' \
  -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$KEY\"}")
if [ "$CODE" = "200" ]; then
  pass "POST /api/auth/login -> 200"
else
  fail "login with the API key as password returned $CODE: $(head -c 120 /tmp/auth-gate-body.json)"
fi
COOKIE=$(grep -i '^set-cookie:' "$LOGIN_HEADERS" | head -1 | sed 's/^[Ss]et-[Cc]ookie: //' | cut -d';' -f1 | tr -d '\r')
CSRF=$(grep -i '^x-csrf-token:' "$LOGIN_HEADERS" | head -1 | sed 's/^[Xx]-[Cc][Ss][Rr][Ff]-[Tt]oken: //' | tr -d '\r')
if [ -n "$COOKIE" ]; then pass "login issued a session cookie"; else fail "login did not issue a session cookie"; fi
if [ -n "$CSRF" ]; then pass "login issued a CSRF token"; else fail "login did not issue a CSRF token"; fi

CODE=$(status GET "/api/collections" -H "Cookie: $COOKIE")
if [ "$CODE" = "200" ]; then
  pass "session cookie authenticates a GET"
else
  fail "GET with session cookie returned $CODE (expected 200)"
fi

CODE=$(status POST "/api/auth/logout" -H "Cookie: $COOKIE" -H "X-CSRF-Token: $CSRF")
if [ "$CODE" = "200" ]; then
  pass "POST /api/auth/logout -> 200"
else
  fail "logout returned $CODE"
fi
CODE=$(status GET "/api/collections" -H "Cookie: $COOKIE")
if [ "$CODE" = "401" ]; then
  pass "session invalidated after logout (401)"
else
  fail "GET after logout returned $CODE (expected 401 — session must be dead)"
fi

echo
echo "========================================"
if [ "$FAILURES" -eq 0 ]; then
  echo "AUTH GATE: PASS (enforcement, rejection, access, and session lifecycle all hold)"
  exit 0
else
  echo "AUTH GATE: FAIL ($FAILURES failure(s))"
  exit 1
fi
