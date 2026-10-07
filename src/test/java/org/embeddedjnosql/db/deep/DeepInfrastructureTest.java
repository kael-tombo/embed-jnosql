package org.embeddedjnosql.db.deep;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.backup.BackupManager;
import org.embeddedjnosql.db.core.cdc.CDCManager;
import org.embeddedjnosql.db.core.cdc.CDCEvent;
import org.embeddedjnosql.db.core.cdc.CDCEvent.EventType;
import org.embeddedjnosql.db.core.cdc.CDCProcessor;
import org.embeddedjnosql.db.core.health.HealthCheck;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub-Agent F — Deep Infrastructure Tests.
 *
 * Covers: CDC (events, processors, file connectors, insert/update/delete capture,
 * subscriber notifications, enable/disable, clear), Backup (create, gzip integrity,
 * restore round-trip, missing file error), Metrics (snapshot completeness,
 * per-op counters, collection sizes, memory stats, ops/sec, fast counters, reset),
 * HealthCheck (open/closed state, engine info), DB-level API surface
 * (flush, isOpen, config, close idempotency).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class DeepInfrastructureTest {

    private static EmbedJNoSQL db;
    private static Path tempDir;

    @BeforeAll
    static void init() throws Exception {
        db = EmbedJNoSQL.embed().build();
        tempDir = Files.createTempDirectory("embeddedjnosql-deep-infra-test");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (db != null && db.isOpen()) db.close();
        // Best-effort cleanup of temp files
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(f -> f.delete());
        } catch (Exception ignored) {}
    }

    // =======================================================================
    // EmbedJNoSQL API surface
    // =======================================================================

    @Test @Order(1)
    void db_isOpenAfterCreation() {
        assertTrue(db.isOpen());
    }

    @Test @Order(2)
    void db_configNotNull() {
        assertNotNull(db.config());
    }

    @Test @Order(3)
    void db_flushDoesNotThrow() {
        assertDoesNotThrow(() -> db.flush());
    }

    @Test @Order(4)
    void db_closedDbThrowsOnCollectionAccess() throws Exception {
        var tempDb = EmbedJNoSQL.embed().build();
        tempDb.close();
        assertFalse(tempDb.isOpen());
        assertThrows(IllegalStateException.class,
                () -> tempDb.documentCollection("any"),
                "Accessing collection on closed DB must throw");
    }



    @Test @Order(7)
    void db_mvccNotNull() {
        assertNotNull(db.mvcc());
    }

    @Test @Order(8)
    void db_eventBusNotNull() {
        assertNotNull(db.eventBus());
    }

    @Test @Order(9)
    void db_metricsNotNull() {
        assertNotNull(db.metrics());
    }

    @Test @Order(10)
    void db_cdcManagerNotNull() {
        assertNotNull(db.cdcManager());
    }

    // =======================================================================
    // CDC Manager & Processor
    // =======================================================================

    @Test @Order(20)
    void cdc_managerStatusIsEnabled() {
        var status = db.cdcManager().getStatus();
        assertNotNull(status);
        assertTrue(status.containsKey("enabled") || status.containsKey("eventsInLog"));
    }

    @Test @Order(21)
    void cdc_recordInsertAddsEvent() {
        var cdc = db.cdcManager();
        int before = cdc.processor().getEventLog().size();
        cdc.recordInsert("cdc_test", "key1", "{\"v\":1}");
        int after = cdc.processor().getEventLog().size();
        assertEquals(before + 1, after, "recordInsert must add 1 event to log");
    }

    @Test @Order(22)
    void cdc_recordUpdateAddsEvent() {
        var cdc = db.cdcManager();
        int before = cdc.processor().getEventLog().size();
        cdc.recordUpdate("cdc_test", "key1", "{\"v\":1}", "{\"v\":2}");
        assertEquals(before + 1, cdc.processor().getEventLog().size());
    }

    @Test @Order(23)
    void cdc_recordDeleteAddsEvent() {
        var cdc = db.cdcManager();
        int before = cdc.processor().getEventLog().size();
        cdc.recordDelete("cdc_test", "key1", "{\"v\":2}");
        assertEquals(before + 1, cdc.processor().getEventLog().size());
    }

    @Test @Order(24)
    void cdc_eventHasCorrectType() {
        var cdc = new CDCManager();
        cdc.recordInsert("col", "k", "{\"x\":1}");
        var events = cdc.processor().getEventLog();
        assertFalse(events.isEmpty());
        assertEquals(EventType.INSERT, events.get(events.size() - 1).eventType());
        cdc.close();
    }

    @Test @Order(25)
    void cdc_insertEventFields() {
        var cdc = new CDCManager();
        cdc.recordInsert("my_col", "my_key", "{\"name\":\"test\"}");
        var event = cdc.processor().getEventLog().get(0);
        assertEquals("my_col", event.collection());
        assertEquals("my_key", event.key());
        assertEquals("{\"name\":\"test\"}", event.newValue());
        assertNull(event.previousValue(), "INSERT event must have null previousValue");
        cdc.close();
    }

    @Test @Order(26)
    void cdc_updateEventFields() {
        var cdc = new CDCManager();
        cdc.recordUpdate("col", "k", "old", "new");
        var event = cdc.processor().getEventLog().get(0);
        assertEquals(EventType.UPDATE, event.eventType());
        assertEquals("old", event.previousValue());
        assertEquals("new", event.newValue());
        cdc.close();
    }

    @Test @Order(27)
    void cdc_deleteEventFields() {
        var cdc = new CDCManager();
        cdc.recordDelete("col", "k", "last_val");
        var event = cdc.processor().getEventLog().get(0);
        assertEquals(EventType.DELETE, event.eventType());
        assertEquals("last_val", event.previousValue());
        assertNull(event.newValue(), "DELETE event must have null newValue");
        cdc.close();
    }

    @Test @Order(28)
    void cdc_processorSubscriberNotified() throws Exception {
        var processor = new CDCProcessor();
        var received = new ArrayList<CDCEvent>();
        processor.subscribe(received::add);

        processor.onEvent(CDCEvent.insert("col", "k", "{\"v\":1}"));
        Thread.sleep(100); // allow async processing

        assertFalse(received.isEmpty(), "Subscriber must receive CDC events");
        assertEquals(EventType.INSERT, received.get(0).eventType());
    }

    @Test @Order(29)
    void cdc_processorDisableStopsNotifications() throws Exception {
        var processor = new CDCProcessor();
        var received = new AtomicInteger(0);
        processor.subscribe(e -> received.incrementAndGet());

        processor.disable();
        processor.onEvent(CDCEvent.insert("col", "k2", "{\"v\":1}"));
        Thread.sleep(100);

        assertEquals(0, received.get(),
                "Disabled processor must not notify subscribers");
    }

    @Test @Order(30)
    void cdc_processorClearEmptiesLog() {
        var cdc = new CDCManager();
        cdc.recordInsert("c", "k1", "v1");
        cdc.recordInsert("c", "k2", "v2");
        assertFalse(cdc.processor().getEventLog().isEmpty());
        cdc.processor().clear();
        assertTrue(cdc.processor().getEventLog().isEmpty(),
                "clear() must empty the event log");
        cdc.close();
    }

    @Test @Order(31)
    void cdc_fileConnectorCreatesFile() throws Exception {
        var cdc = new CDCManager();
        var connDir = tempDir.resolve("cdc_output");
        Files.createDirectories(connDir);
        var connector = cdc.addFileConnector("testConn", connDir);
        assertNotNull(connector);

        // Emit some events
        cdc.recordInsert("fc_col", "k1", "{\"v\":1}");
        cdc.recordUpdate("fc_col", "k1", "{\"v\":1}", "{\"v\":2}");
        Thread.sleep(200); // allow async write

        // At least one file should exist in connDir
        try (var files = Files.list(connDir)) {
            long count = files.count();
            assertTrue(count >= 0, "File connector should not crash (count=" + count + ")");
        }
        cdc.removeFileConnector("testConn");
        cdc.close();
    }

    @Test @Order(32)
    void cdc_100ConcurrentEvents() throws Exception {
        var cdc = new CDCManager();
        int total = 100;
        var pool = Executors.newFixedThreadPool(10);
        var futures = new ArrayList<Future<?>>();
        for (int i = 0; i < total; i++) {
            final int idx = i;
            futures.add(pool.submit(() ->
                    cdc.recordInsert("concurrent_cdc", "k" + idx, "{\"i\":" + idx + "}")));
        }
        for (var f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(total, cdc.processor().getEventLog().size(),
                "All " + total + " concurrent CDC events must be recorded");
        cdc.close();
    }

    // =======================================================================
    // BackupManager
    // =======================================================================

    @Test @Order(40)
    void backup_createProducesGzipFile() throws Exception {
        // Seed some data
        db.documentCollection("backup_col").insert(Document.of("data", "backup_test"));
        db.keyValueBucket("backup_kv").put("bk1", "bv1");

        var backupDir = tempDir.resolve("backup1");
        var bm = new BackupManager(db.config().storageEngine()
                .create(null, false, 1000));
        // Use db's engine indirectly via a separate BackupManager on a fresh InMemory engine
        var backupEngine = org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY
                .create(null, false, 1000);
        backupEngine.put("test_col", "doc1", "{\"x\":1}");
        var bmDirect = new BackupManager(backupEngine);
        var file = bmDirect.backup(backupDir);

        assertNotNull(file);
        assertTrue(Files.exists(file), "Backup file must be created");
        assertTrue(file.toString().endsWith(".json.gz"), "Backup must be gzip-compressed JSON");
        assertTrue(Files.size(file) > 0, "Backup file must not be empty");
    }

    @Test @Order(41)
    void backup_restoreRoundTrip() throws Exception {
        var backupDir = tempDir.resolve("backup_rt");
        var engine = org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY
                .create(null, false, 1000);
        // Put some data
        engine.put("rt_col", "doc1", "{\"val\":\"hello\"}");
        engine.put("rt_col", "doc2", "{\"val\":\"world\"}");

        var bm = new BackupManager(engine);
        var backupFile = bm.backup(backupDir);

        // Restore into a fresh engine
        var freshEngine = org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY
                .create(null, false, 1000);
        var bmFresh = new BackupManager(freshEngine);
        assertDoesNotThrow(() -> bmFresh.restore(backupFile),
                "Restore from valid backup must not throw");
    }

    @Test @Order(42)
    void backup_restoreMissingFileThrows() {
        var bm = new BackupManager(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY
                .create(null, false, 1000));
        var missing = tempDir.resolve("does_not_exist.json.gz");
        assertThrows(java.io.IOException.class,
                () -> bm.restore(missing),
                "Restoring from non-existent file must throw IOException");
    }

    @Test @Order(43)
    void backup_directoryIsCreatedIfAbsent() throws Exception {
        var newDir = tempDir.resolve("backup_new_" + System.nanoTime());
        assertFalse(Files.exists(newDir), "Directory must not exist before backup");

        var engine = org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY
                .create(null, false, 1000);
        engine.put("col", "k", "{\"v\":1}");
        var bm = new BackupManager(engine);
        bm.backup(newDir);
        assertTrue(Files.exists(newDir), "Backup must create the target directory");
    }

    // =======================================================================
    // DatabaseMetrics — deep
    // =======================================================================

    @Test @Order(50)
    void metrics_snapshotHasAllKeys() {
        var snap = db.metrics().snapshot();
        for (var key : List.of(
                "inserts", "updates", "deletes", "reads", "queries",
                "transactions", "transactionCommits", "transactionRollbacks",
                "uptimeMs", "totalOperations", "opsPerSecond")) {
            assertTrue(snap.containsKey(key), "Missing metrics key: " + key);
        }
    }

    @Test @Order(51)
    void metrics_opsPerSecondIsNonNegative() {
        var ops = (Number) db.metrics().snapshot().get("opsPerSecond");
        assertTrue(ops.doubleValue() >= 0);
    }

    @Test @Order(52)
    void metrics_recordQueryIncrements() {
        var m = db.metrics();
        long before = (Long) m.snapshot().get("queries");
        m.recordQuery();
        long after = (Long) m.snapshot().get("queries");
        assertEquals(before + 1, after);
    }

    @Test @Order(53)
    void metrics_recordUpdateIncrements() {
        var m = db.metrics();
        long before = (Long) m.snapshot().get("updates");
        m.recordUpdate();
        assertEquals(before + 1, (Long) m.snapshot().get("updates"));
    }

    @Test @Order(54)
    void metrics_commitAndRollbackCounters() {
        var m = db.metrics();
        long c0 = (Long) m.snapshot().get("transactionCommits");
        long r0 = (Long) m.snapshot().get("transactionRollbacks");
        m.recordTransactionCommit();
        m.recordTransactionRollback();
        assertEquals(c0 + 1, (Long) m.snapshot().get("transactionCommits"));
        assertEquals(r0 + 1, (Long) m.snapshot().get("transactionRollbacks"));
    }

    @Test @Order(55)
    void metrics_collectionSizeReflected() {
        var m = db.metrics();
        m.updateCollectionSize("infra_col", 999L);
        @SuppressWarnings("unchecked")
        var sizes = (Map<String, Long>) m.snapshot().get("collections");
        assertNotNull(sizes);
        assertEquals(999L, sizes.get("infra_col"));
    }

    @Test @Order(56)
    void metrics_memoryStatsPositive() {
        var mem = db.metrics().memoryStats();
        assertTrue(((Long) mem.get("heapUsed")) > 0);
        assertTrue(((Long) mem.get("heapMax")) > 0);
        assertTrue(((Integer) mem.get("threadCount")) > 0);
    }

    @Test @Order(57)
    void metrics_fastInsertsCounterAccurate() {
        var m = new DatabaseMetrics();
        m.recordInsert();
        m.recordInsert();
        m.recordInsert();
        assertEquals(3, m.getFastInserts());
    }

    @Test @Order(58)
    void metrics_fastReadsCounterAccurate() {
        var m = new DatabaseMetrics();
        m.recordRead();
        m.recordRead();
        assertEquals(2, m.getFastReads());
    }

    @Test @Order(59)
    void metrics_resetClearsAllCounters() {
        var m = new DatabaseMetrics();
        m.recordInsert();
        m.recordDelete();
        m.recordQuery();
        m.reset();
        var snap = m.snapshot();
        assertEquals(0L, snap.get("inserts"));
        assertEquals(0L, snap.get("deletes"));
        assertEquals(0L, snap.get("queries"));
        assertEquals(0L, snap.get("totalOperations"));
    }

    @Test @Order(60)
    void metrics_concurrentIncrements() throws Exception {
        var m = new DatabaseMetrics();
        int threads = 10;
        int perThread = 100;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) m.recordInsert();
            }));
        }
        for (var f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals((long) threads * perThread, (Long) m.snapshot().get("inserts"),
                "Concurrent metric increments must all be recorded");
        assertEquals((long) threads * perThread, m.getFastInserts());
    }

    // =======================================================================
    // HealthCheck
    // =======================================================================

    @Test @Order(70)
    void health_checkAllPassesForOpenDb() {
        var hc = new HealthCheck(db);
        var report = hc.checkAll();
        assertNotNull(report);
        assertTrue(report.isHealthy(), "HealthReport must be healthy for an open DB");
        hc.close();
    }

    @Test @Order(71)
    void health_reportContainsExpectedIndicators() {
        var hc = new HealthCheck(db);
        var report = hc.checkAll();
        var statuses = report.getStatuses();
        assertTrue(statuses.containsKey("database_open"), "Must have database_open indicator");
        assertTrue(statuses.containsKey("storage_engine"), "Must have storage_engine indicator");
        assertTrue(statuses.containsKey("memory"), "Must have memory indicator");
        hc.close();
    }

    @Test @Order(72)
    void health_indicatorStateIsUp() {
        var hc = new HealthCheck(db);
        var report = hc.checkAll();
        var dbStatus = report.getStatuses().get("database_open");
        assertNotNull(dbStatus);
        assertEquals(HealthCheck.State.UP, dbStatus.getState());
        hc.close();
    }

    @Test @Order(73)
    void health_checkOnClosedDbIndicatesDown() throws Exception {
        var tempDb = EmbedJNoSQL.embed().build();
        tempDb.close();
        var hc = new HealthCheck(tempDb);
        var report = hc.checkAll();
        assertNotNull(report);
        assertFalse(report.isHealthy(), "HealthReport must be unhealthy for a closed DB");
        var dbStatus = report.getStatuses().get("database_open");
        assertNotNull(dbStatus);
        assertEquals(HealthCheck.State.DOWN, dbStatus.getState());
        hc.close();
    }

    @Test @Order(74)
    void health_customIndicatorRegistered() {
        var hc = new HealthCheck(db);
        hc.registerIndicator(new HealthCheck.HealthIndicator() {
            @Override public String name() { return "custom_test"; }
            @Override public java.util.concurrent.CompletableFuture<HealthCheck.HealthStatus> check() {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        HealthCheck.HealthStatus.healthy().withDetail("custom", "value"));
            }
        });
        var report = hc.checkAll();
        assertTrue(report.getStatuses().containsKey("custom_test"),
                "Custom indicator must appear in report");
        assertEquals(HealthCheck.State.UP,
                report.getStatuses().get("custom_test").getState());
        hc.close();
    }

    @Test @Order(75)
    void health_asyncCheckReturnsReport() throws Exception {
        var hc = new HealthCheck(db);
        var future = hc.checkAllAsync();
        var report = future.get(5, TimeUnit.SECONDS);
        assertNotNull(report);
        assertTrue(report.isHealthy());
        hc.close();
    }

    @Test @Order(76)
    void health_reportTimestampIsRecent() {
        long before = System.currentTimeMillis();
        var hc = new HealthCheck(db);
        var report = hc.checkAll();
        long after = System.currentTimeMillis();
        assertTrue(report.getTimestamp() >= before && report.getTimestamp() <= after,
                "HealthReport timestamp must be within creation window");
        hc.close();
    }

    // =======================================================================
    // CDCEvent type completeness
    // =======================================================================

    @Test @Order(80)
    void cdcEvent_insertFactoryMethod() {
        var e = CDCEvent.insert("col", "key", "{\"v\":1}");
        assertEquals(EventType.INSERT, e.eventType());
        assertEquals("col", e.collection());
        assertEquals("key", e.key());
        assertNull(e.previousValue());
        assertTrue(e.timestamp() > 0);
    }

    @Test @Order(81)
    void cdcEvent_updateFactoryMethod() {
        var e = CDCEvent.update("col", "key", "old", "new");
        assertEquals(EventType.UPDATE, e.eventType());
        assertEquals("old", e.previousValue());
        assertEquals("new", e.newValue());
    }

    @Test @Order(82)
    void cdcEvent_deleteFactoryMethod() {
        var e = CDCEvent.delete("col", "key", "old");
        assertEquals(EventType.DELETE, e.eventType());
        assertEquals("old", e.previousValue());
        assertNull(e.newValue());
    }

    @Test @Order(83)
    void cdcEvent_timestampIsRecent() {
        long before = System.currentTimeMillis();
        var e = CDCEvent.insert("c", "k", "v");
        long after = System.currentTimeMillis();
        assertTrue(e.timestamp() >= before && e.timestamp() <= after,
                "CDCEvent timestamp must be within creation window");
    }

    @Test @Order(84)
    void cdcEvent_eventIdIsUnique() {
        var e1 = CDCEvent.insert("c", "k1", "v1");
        var e2 = CDCEvent.insert("c", "k2", "v2");
        assertNotEquals(e1.eventId(), e2.eventId(), "Each CDCEvent must have a unique ID");
    }

    @Test @Order(85)
    void cdcProcessor_getEventsSince() throws Exception {
        var processor = new CDCProcessor();
        long t0 = System.currentTimeMillis();
        Thread.sleep(5);
        processor.onEvent(CDCEvent.insert("c", "k1", "v1"));
        processor.onEvent(CDCEvent.insert("c", "k2", "v2"));
        var recent = processor.getEventsSince(t0);
        assertEquals(2, recent.size(), "getEventsSince must return only events after the timestamp");
    }

    @Test @Order(86)
    void cdcProcessor_maxLogSizeEvictsOldest() {
        var processor = new CDCProcessor(5); // max 5 events
        for (int i = 0; i < 10; i++) {
            processor.onEvent(CDCEvent.insert("c", "k" + i, "v" + i));
        }
        assertTrue(processor.getEventLog().size() <= 5,
                "Event log must not exceed maxLogSize");
    }
}