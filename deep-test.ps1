$BASE   = "http://localhost:8080"
$APIKEY = "hYXuECpj4dM28vf3En47ar2KaA1FPMLVNrmAYYSAoFM"
$HDR    = @{ "X-API-Key" = $APIKEY; "Content-Type" = "application/json" }
$PASS = 0; $FAIL = 0; $WARN = 0
$DETAILS = [System.Collections.Generic.List[string]]::new()

function T {
    param($label, $method, $path, $body=$null, $expectCode=200)
    $url = "$BASE$path"
    try {
        $params = @{ Method=$method; Uri=$url; Headers=$HDR; ErrorAction="Stop"; UseBasicParsing=$true }
        if ($body) { $params.Body = ($body | ConvertTo-Json -Depth 10) }
        $resp = Invoke-WebRequest @params
        $code = $resp.StatusCode
        if ($code -eq $expectCode) {
            Write-Host "  [PASS] $label" -ForegroundColor Green
            $script:PASS++
            return $resp.Content | ConvertFrom-Json -ErrorAction SilentlyContinue
        } else {
            Write-Host "  [FAIL] $label  (HTTP $code, expected $expectCode)" -ForegroundColor Red
            $script:FAIL++
            $script:DETAILS.Add("FAIL: $label => HTTP $code (expected $expectCode) | $($resp.Content.Substring(0,[Math]::Min(150,$resp.Content.Length)))")
            return $null
        }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($code -eq $expectCode) {
            Write-Host "  [PASS] $label (expected $expectCode)" -ForegroundColor Green
            $script:PASS++
        } else {
            Write-Host "  [FAIL] $label  (HTTP $code, expected $expectCode)" -ForegroundColor Red
            $script:FAIL++
            $script:DETAILS.Add("FAIL: $label => HTTP $code | $($_.Exception.Message)")
        }
        return $null
    }
}

function TNoAuth {
    param($label, $path, $expectCode=200)
    $url = "$BASE$path"
    try {
        $resp = Invoke-WebRequest -Method GET -Uri $url -UseBasicParsing -ErrorAction Stop
        $code = $resp.StatusCode
        if ($code -eq $expectCode) { Write-Host "  [PASS] $label" -ForegroundColor Green; $script:PASS++ }
        else { Write-Host "  [FAIL] $label (HTTP $code, expected $expectCode)" -ForegroundColor Red; $script:FAIL++ }
    } catch {
        $code = 0; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($code -eq $expectCode) { Write-Host "  [PASS] $label (expected $expectCode)" -ForegroundColor Green; $script:PASS++ }
        else { Write-Host "  [FAIL] $label (HTTP $code, expected $expectCode)" -ForegroundColor Red; $script:FAIL++ }
    }
}

Write-Host "=== JunifyDB DEEP FEATURE TEST SUITE ===" -ForegroundColor Magenta

# ---- 1. HEALTH ----
Write-Host "`n--- 1. HEALTH CHECK ---" -ForegroundColor Cyan
$h = T "GET /api/health" GET "/api/health"
if ($h -and $h.status -eq "ok") { Write-Host "  [PASS] health.status = ok" -ForegroundColor Green; $PASS++ }
else { Write-Host "  [FAIL] health.status != ok (got: $($h.status))" -ForegroundColor Red; $FAIL++ }

# ---- 2. AUTH ----
Write-Host "`n--- 2. AUTHENTICATION ---" -ForegroundColor Cyan
TNoAuth "GET /api/health without key should 401" "/api/health" 401
T "POST /api/auth/login valid creds" POST "/api/auth/login" @{username="admin";password="admin123"} 200
T "POST /api/auth/login bad password should 401" POST "/api/auth/login" @{username="admin";password="wrongpass"} 401

# ---- 3. METRICS ----
Write-Host "`n--- 3. METRICS AND STATS ---" -ForegroundColor Cyan
T "GET /api/metrics" GET "/api/metrics"
T "GET /api/stats" GET "/api/stats"

# ---- 4. COLLECTIONS ----
Write-Host "`n--- 4. DOCUMENT COLLECTIONS ---" -ForegroundColor Cyan
$R = Get-Random -Maximum 9999
$col = "test_col_$R"
$ins1 = T "POST collection insert doc1" POST "/api/collections/$col" @{name="Alice";age=30;city="Paris"} 201
$ins2 = T "POST collection insert doc2" POST "/api/collections/$col" @{name="Bob";age=25;city="London"} 201
$ins3 = T "POST collection insert doc3" POST "/api/collections/$col" @{name="Carol";age=35;city="Paris"} 201
T "GET collection list all" GET "/api/collections/$col"
if ($ins1 -and $ins1.id) { T "GET collection by id" GET "/api/collections/$col/$($ins1.id)" }
T "POST collection query eq" POST "/api/collections/$col/query" @{city="Paris"} 200
T "POST collection query gt" POST "/api/collections/$col/query" @{'$gt'=@{age=28}} 200
T "POST collection query lt" POST "/api/collections/$col/query" @{'$lt'=@{age=32}} 200
T "GET collection stats" GET "/api/collections/$col/stats"
if ($ins1 -and $ins1.id) {
    T "POST set-ttl on doc" POST "/api/collections/$col/set-ttl" @{documentId=$ins1.id;ttlSeconds=3600} 200
}
T "POST cleanup expired" POST "/api/collections/$col/cleanup" @{} 200
if ($ins2 -and $ins2.id) {
    T "PUT update doc" PUT "/api/collections/$col/$($ins2.id)" @{name="Bob Updated";age=26;city="Berlin"} 201
}
if ($ins3 -and $ins3.id) {
    T "DELETE doc" DELETE "/api/collections/$col/$($ins3.id)" 204
    T "GET deleted doc should 404" GET "/api/collections/$col/$($ins3.id)" 404
}

# ---- 5. KEY-VALUE ----
Write-Host "`n--- 5. KEY-VALUE STORE ---" -ForegroundColor Cyan
$kv = "kv_$R"
T "PUT kv key1" PUT "/api/kv/$kv/key1" @{value="hello-world"} 201
T "GET kv key1" GET "/api/kv/$kv/key1"
T "PUT kv key2" PUT "/api/kv/$kv/key2" @{value="42"} 201
T "DELETE kv key1" DELETE "/api/kv/$kv/key1" 204
T "GET deleted kv key should 404" GET "/api/kv/$kv/key1" 404

# ---- 6. LIST BUCKET ----
Write-Host "`n--- 6. LIST BUCKET ---" -ForegroundColor Cyan
$lb = "lb_$R"
T "LIST rpush alpha" POST "/api/kv/lists/$lb/mylist/rpush" @{value="alpha"} 200
T "LIST rpush beta" POST "/api/kv/lists/$lb/mylist/rpush" @{value="beta"} 200
T "LIST lpush zero" POST "/api/kv/lists/$lb/mylist/lpush" @{value="zero"} 200
T "LIST lrange" GET "/api/kv/lists/$lb/mylist/lrange"
T "LIST llen" GET "/api/kv/lists/$lb/mylist/llen"
T "LIST lpop" POST "/api/kv/lists/$lb/mylist/lpop" @{} 200
T "LIST rpop" POST "/api/kv/lists/$lb/mylist/rpop" @{} 200

# ---- 7. SET BUCKET ----
Write-Host "`n--- 7. SET BUCKET ---" -ForegroundColor Cyan
$sb = "sb_$R"
T "SET sadd a" POST "/api/kv/sets/$sb/myset/sadd" @{member="a"} 200
T "SET sadd b" POST "/api/kv/sets/$sb/myset/sadd" @{member="b"} 200
T "SET sadd c" POST "/api/kv/sets/$sb/myset/sadd" @{member="c"} 200
T "SET smembers" GET "/api/kv/sets/$sb/myset/smembers"
T "SET sismember b" POST "/api/kv/sets/$sb/myset/sismember" @{member="b"} 200
T "SET scard" GET "/api/kv/sets/$sb/myset/scard"
T "SET srem a" POST "/api/kv/sets/$sb/myset/srem" @{member="a"} 200

# ---- 8. HASH BUCKET ----
Write-Host "`n--- 8. HASH BUCKET ---" -ForegroundColor Cyan
$hb = "hb_$R"
T "HASH hset name" POST "/api/kv/hashes/$hb/user1/hset" @{field="name";value="Alice"} 200
T "HASH hset age" POST "/api/kv/hashes/$hb/user1/hset" @{field="age";value="30"} 200
T "HASH hgetall" GET "/api/kv/hashes/$hb/user1/hgetall"
T "HASH hget name" GET "/api/kv/hashes/$hb/user1/hget/name"
T "HASH hlen" GET "/api/kv/hashes/$hb/user1/hlen"
T "HASH hdel age" POST "/api/kv/hashes/$hb/user1/hdel" @{field="age"} 200

# ---- 9. COLUMN FAMILY ----
Write-Host "`n--- 9. COLUMN FAMILY ---" -ForegroundColor Cyan
$cf = "cf_$R"
T "COL PUT row1" PUT "/api/columns/$cf/row1" @{col1="val1";col2="val2"} 201
T "COL GET row1" GET "/api/columns/$cf/row1"
T "COL PUT row2" PUT "/api/columns/$cf/row2" @{col1="x";col2="y"} 201
T "COL GET all" GET "/api/columns/$cf"
T "COL DELETE row1" DELETE "/api/columns/$cf/row1" 204

# ---- 10. DOCUMENT QUERY ----
# The SQL engine and POST /api/sql were removed from the product; the removed route now answers
# 404 and is asserted below. Document queries are the supported path.
Write-Host "`n--- 10. DOCUMENT QUERY ---" -ForegroundColor Cyan
$dq = "dq_$R"
T "DOC insert 1" POST "/api/collections/$dq" @{id="d1";name="Keyboard";stock=41} 201
T "DOC insert 2" POST "/api/collections/$dq" @{id="d2";name="Mouse Pad";stock=7} 201
T "DOC query all" POST "/api/collections/$dq/query" @{filter=@{}} 200
T "DOC query eq" POST "/api/collections/$dq/query" @{filter=@{name=@{'$eq'="Keyboard"}}} 200
T "DOC query range" POST "/api/collections/$dq/query" @{filter=@{stock=@{'$gt'=10}}} 200
T "DOC query regex" POST "/api/collections/$dq/query" @{filter=@{name=@{'$regex'="Key"}}} 200
T "DOC query and" POST "/api/collections/$dq/query" @{filter=@{'$and'=@(@{name=@{'$eq'="Keyboard"}},@{stock=@{'$gt'=10}})}} 200
T "DOC update" PUT "/api/collections/$dq/d1" @{id="d1";name="Keyboard";stock=39} 200
T "DOC delete" DELETE "/api/collections/$dq/d2" 204
T "SQL endpoint removed" POST "/api/sql" @{query="SELECT 1"} 404

# ---- 11. SCHEMA ----
Write-Host "`n--- 11. SCHEMA MANAGEMENT ---" -ForegroundColor Cyan
T "SCHEMA list" GET "/api/schema/"
T "SCHEMA create" POST "/api/schema/" @{name="sch_$R"} 200

# ---- 12. VECTORS ----
Write-Host "`n--- 12. VECTOR SEARCH ---" -ForegroundColor Cyan
$vc = "vc_$R"
T "VEC insert v1" POST "/api/vectors/$vc" @{id="v1";vector=@(0.1,0.2,0.3,0.4);metadata=@{label="A"}} 201
T "VEC insert v2" POST "/api/vectors/$vc" @{id="v2";vector=@(0.9,0.8,0.7,0.6);metadata=@{label="B"}} 201
T "VEC insert v3" POST "/api/vectors/$vc" @{id="v3";vector=@(0.1,0.15,0.25,0.35);metadata=@{label="C"}} 201
T "VEC search knn=2" POST "/api/vectors/$vc/search" @{vector=@(0.1,0.2,0.3,0.4);k=2} 200
T "VEC get by id" GET "/api/vectors/$vc/v1"
T "VEC delete v2" DELETE "/api/vectors/$vc/v2" 204

# ---- 13. BULK ----
Write-Host "`n--- 13. BULK OPERATIONS ---" -ForegroundColor Cyan
T "BULK batch insert" POST "/api/bulk" @{
    collection="bulk_$R"
    operations=@(
        @{type="insert";data=@{name="X1";val=1}},
        @{type="insert";data=@{name="X2";val=2}},
        @{type="insert";data=@{name="X3";val=3}}
    )
} 200

# ---- 14. TRANSACTIONS ----
Write-Host "`n--- 14. TRANSACTIONS ---" -ForegroundColor Cyan
T "TXN begin" POST "/api/transactions" @{action="begin"} 200
T "TXN status" GET "/api/transactions"
T "TXN commit" POST "/api/transactions" @{action="commit"} 200
T "TXN rollback" POST "/api/transactions" @{action="rollback"} 200

# ---- 15. INDEXES ----
Write-Host "`n--- 15. INDEXES ---" -ForegroundColor Cyan
T "IDX list" GET "/api/indexes/"
T "IDX create" POST "/api/indexes/" @{collection="test_col_$R";field="name";type="hash"} 200

# ---- 16. BACKUP ----
Write-Host "`n--- 16. BACKUP ---" -ForegroundColor Cyan
T "BACKUP trigger" POST "/api/backup" @{} 200
T "BACKUP list" GET "/api/backup"

# ---- 17. TABLES & CONSTRAINTS ----
Write-Host "`n--- 17. TABLES AND CONSTRAINTS ---" -ForegroundColor Cyan
T "TABLES list" GET "/api/tables/"
T "CONSTRAINTS list" GET "/api/constraints/"

# ---- 18. AUDIT LOG ----
Write-Host "`n--- 18. AUDIT LOG ---" -ForegroundColor Cyan
T "AUDIT get all" GET "/api/audit/logs"
T "AUDIT limit 10" GET "/api/audit/logs?limit=10"
T "AUDIT filter operation" GET "/api/audit/logs?operation=INSERT"

# ---- 19. CDC ----
Write-Host "`n--- 19. CDC ---" -ForegroundColor Cyan
T "CDC events" GET "/api/cdc"

# ---- 20. STATIC FILES ----
Write-Host "`n--- 20. FRONTEND STATIC FILES ---" -ForegroundColor Cyan
TNoAuth "GET / index.html" "/"
TNoAuth "GET /login.html" "/login.html"
TNoAuth "GET /logo.svg" "/logo.svg"
TNoAuth "GET /favicon.svg" "/favicon.svg"

# ---- SUMMARY ----
Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "  PASSED : $PASS" -ForegroundColor Green
Write-Host "  FAILED : $FAIL" -ForegroundColor $(if($FAIL -gt 0){"Red"}else{"Green"})
Write-Host "  TOTAL  : $($PASS+$FAIL)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Magenta

if ($FAIL -gt 0) {
    Write-Host "`nFAILED DETAILS:" -ForegroundColor Red
    foreach ($d in $DETAILS) { Write-Host "  $d" -ForegroundColor Red }
}
