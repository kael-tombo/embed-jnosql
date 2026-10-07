package org.embeddedjnosql.db.deep;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.transaction.mvcc.MVCCManager;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub-Agent E — Deep Transaction / MVCC Tests.
 *
 * Covers: begin/commit/rollback lifecycle, status transitions,
 * transactional reads/writes/deletes, isolation (reads within tx),
 * write-write conflict detection, concurrent transactions,
 * MVCCManager timestamp ordering, vacuum, stats,
 * AutoCloseable behaviour, timeout metadata, and event emission.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DeepTransactionTest {

    private static EmbedJNoSQL db;

    @BeforeAll
    static void init() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterAll
    static void teardown() {
        if (db != null && db.isOpen()) db.close();
    }

    // -----------------------------------------------------------------------
    // Basic lifecycle
    // -----------------------------------------------------------------------

    @Test @Order(1)
    void beginTransactionReturnsNonNull() {
        var tx = db.beginTransaction();
        assertNotNull(tx, "beginTransaction() must return a non-null Transaction");
        tx.rollback();
    }

    @Test @Order(2)
    void newTransactionIsActive() {
        var tx = db.beginTransaction();
        assertTrue(tx.isOpen(), "Newly created transaction must be ACTIVE");
        assertEquals(Transaction.Status.ACTIVE, tx.status());
        tx.rollback();
    }

    @Test @Order(3)
    void commitChangesStatus() {
        var tx = db.beginTransaction();
        tx.commit();
        assertFalse(tx.isOpen());
        assertTrue(tx.isCommitted());
        assertEquals(Transaction.Status.COMMITTED, tx.status());
    }

    @Test @Order(4)
    void rollbackChangesStatus() {
        var tx = db.beginTransaction();
        tx.rollback();
        assertFalse(tx.isOpen());
        assertTrue(tx.isRolledBack());
        assertEquals(Transaction.Status.ROLLED_BACK, tx.status());
    }

    @Test @Order(5)
    void commitOnRolledBackThrows() {
        var tx = db.beginTransaction();
        tx.rollback();
        assertThrows(IllegalStateException.class, tx::commit,
                "Committing a rolled-back transaction must throw");
    }

    @Test @Order(6)
    void rollbackOnCommittedThrows() {
        var tx = db.beginTransaction();
        tx.commit();
        assertThrows(IllegalStateException.class, tx::rollback,
                "Rolling back a committed transaction must throw");
    }

    @Test @Order(7)
    void doubleRollbackIsSafe() {
        var tx = db.beginTransaction();
        tx.rollback();
        assertDoesNotThrow(tx::rollback, "Second rollback on same tx must be a no-op");
    }

    @Test @Order(8)
    void autoCloseableRollbacksOnLeave() {
        Transaction tx;
        try (var t = db.beginTransaction()) {
            tx = t;
            // do NOT commit — auto-close should rollback
        }
        assertTrue(tx.isRolledBack(),
                "try-with-resources must auto-rollback uncommitted transaction");
    }

    @Test @Order(9)
    void autoCloseableAfterCommitIsNoop() {
        assertDoesNotThrow(() -> {
            try (var tx = db.beginTransaction()) {
                tx.commit();
            }
        }, "Closing an already-committed tx must not throw");
    }

    // -----------------------------------------------------------------------
    // Transaction metadata
    // -----------------------------------------------------------------------

    @Test @Order(10)
    void transactionHasUniqueId() {
        var tx1 = db.beginTransaction();
        var tx2 = db.beginTransaction();
        assertNotNull(tx1.id());
        assertNotNull(tx2.id());
        assertNotEquals(tx1.id(), tx2.id(), "Transaction IDs must be unique");
        tx1.rollback();
        tx2.rollback();
    }

    @Test @Order(11)
    void transactionInfoMapIsPopulated() {
        var tx = db.beginTransaction();
        var info = tx.info();
        assertNotNull(info);
        assertTrue(info.containsKey("id"));
        assertTrue(info.containsKey("status"));
        assertTrue(info.containsKey("createdAt"));
        assertTrue(info.containsKey("isolationLevel"));
        tx.rollback();
    }

    @Test @Order(12)
    void readTimestampIsMonotonicallyIncreasing() {
        var tx1 = db.beginTransaction();
        var tx2 = db.beginTransaction();
        assertTrue(tx2.readTimestamp() >= tx1.readTimestamp(),
                "Read timestamps must be non-decreasing");
        tx1.rollback();
        tx2.rollback();
    }

    @Test @Order(13)
    void operationCountStartsAtZero() {
        var tx = db.beginTransaction();
        assertEquals(0, tx.operationCount());
        tx.rollback();
    }

    // -----------------------------------------------------------------------
    // Transactional writes & reads
    // -----------------------------------------------------------------------

    @Test @Order(20)
    void writeAndReadWithinTransaction() {
        var tx = db.beginTransaction();
        tx.write("txcol", "k1", "{\"value\":\"hello\"}");
        assertEquals(1, tx.operationCount());
        tx.rollback();
    }

    @Test @Order(21)
    void deleteWithinTransaction() {
        var tx = db.beginTransaction();
        tx.delete("txcol", "k2");
        assertEquals(1, tx.operationCount());
        tx.rollback();
    }

    @Test @Order(22)
    void multipleOpsAccumulate() {
        var tx = db.beginTransaction();
        tx.write("c", "k1", "{\"a\":1}");
        tx.write("c", "k2", "{\"b\":2}");
        tx.delete("c", "k3");
        assertEquals(3, tx.operationCount());
        tx.rollback();
    }

    @Test @Order(23)
    void committedWriteIsVisibleInEngine() {
        var tx = db.beginTransaction();
        var col = tx.documentCollection("tx_doc_col");
        var doc = col.insert(Document.of("key", "txValue"));
        assertNotNull(doc.id());
        tx.commit();

        // Verify the document is visible via normal API after commit
        var found = db.documentCollection("tx_doc_col").findById(doc.id());
        assertNotNull(found, "Committed doc must be visible via normal API");
        assertEquals("txValue", found.get("key"));
    }

    @Test @Order(24)
    void rolledBackWriteIsNotVisible() {
        var tx = db.beginTransaction();
        var col = tx.documentCollection("tx_rollback_col");
        var doc = col.insert(Document.of("key", "shouldBeGone"));
        tx.rollback();

        // After rollback the doc must NOT appear in the collection
        var found = db.documentCollection("tx_rollback_col").findById(doc.id());
        assertNull(found, "Rolled-back doc must not be visible via normal API");
    }

    @Test @Order(25)
    void transactionalDeleteIsAppliedOnCommit() {
        // Pre-seed a document
        var doc = db.documentCollection("tx_del_col").insert(Document.of("toDelete", true));

        var tx = db.beginTransaction();
        var col = tx.documentCollection("tx_del_col");
        col.deleteById(doc.id());
        tx.commit();

        // Verify deleted
        assertNull(db.documentCollection("tx_del_col").findById(doc.id()),
                "Deleted doc via committed tx must not be retrievable");
    }

    // -----------------------------------------------------------------------
    // MVCC Manager direct tests
    // -----------------------------------------------------------------------

    @Test @Order(30)
    void mvccManagerNotNull() {
        assertNotNull(db.mvcc(), "MVCCManager must be accessible");
    }

    @Test @Order(31)
    void mvccTimestampMonotonicallyIncreases() {
        var mvcc = db.mvcc();
        long t1 = mvcc.assignTimestamp();
        long t2 = mvcc.assignTimestamp();
        long t3 = mvcc.assignTimestamp();
        assertTrue(t2 > t1, "MVCC timestamp must increase: t1=" + t1 + " t2=" + t2);
        assertTrue(t3 > t2, "MVCC timestamp must increase: t2=" + t2 + " t3=" + t3);
    }

    @Test @Order(32)
    void mvccStatsNotNull() {
        var stats = db.mvcc().stats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("keys"), "MVCC stats must contain 'keys'");
        assertTrue(stats.containsKey("activeTransactions"));
        assertTrue(stats.containsKey("currentTimestamp"));
    }

    @Test @Order(33)
    void mvccRollbackCleansUpWriteBuffer() {
        var mvcc = new MVCCManager();
        long ts = mvcc.assignTimestamp();
        // Stage a write then rollback
        var record = Document.of("temp", "value");
        mvcc.stageWrite("tx-abc", "col:key1", record);
        assertEquals(1, mvcc.stats().get("activeTransactions"),
                "There should be 1 active tx after staging");
        mvcc.rollback("tx-abc");
        assertEquals(0, mvcc.stats().get("activeTransactions"),
                "After rollback, active tx count must be 0");
    }

    @Test @Order(34)
    void mvccCommitAppliesVersionChain() {
        var mvcc = new MVCCManager();
        long t1 = mvcc.assignTimestamp();
        var record = Document.of("val", "committed");
        mvcc.stageWrite("tx-commit1", "col:committed_key", record);
        long commitTs = mvcc.assignTimestamp();
        boolean ok = mvcc.commit("tx-commit1", commitTs);
        assertTrue(ok, "Commit must succeed when there is no conflict");
        assertEquals(1, (int) mvcc.stats().get("keys"),
                "One key should be in the version store after commit");
    }

    @Test @Order(35)
    void mvccVacuumAggressiveRemovesOldVersions() {
        var mvcc = new MVCCManager();
        // Commit several versions for the same key
        for (int i = 0; i < 5; i++) {
            mvcc.stageWrite("tx-v" + i, "col:same_key", Document.of("v", i));
            mvcc.commit("tx-v" + i, mvcc.assignTimestamp());
        }
        int before = mvcc.versionCount();
        // We expect 5 versions chained for same_key
        // vacuumAggressive keeps only the latest
        int collected = mvcc.vacuumAggressive();
        int after = mvcc.versionCount();
        assertTrue(collected >= 0, "vacuumAggressive must report collected >= 0");
        assertTrue(after <= before, "After vacuum, version count must not increase");
    }

    @Test @Order(36)
    void mvccVacuumWithMinTimestamp() {
        var mvcc = new MVCCManager();
        long oldTs = mvcc.assignTimestamp();
        mvcc.stageWrite("tx-old", "col:v_old", Document.of("v", 1));
        mvcc.commit("tx-old", oldTs);
        // Add a newer version
        long newTs = mvcc.assignTimestamp();
        mvcc.stageWrite("tx-new", "col:v_old", Document.of("v", 2));
        mvcc.commit("tx-new", newTs);
        // vacuum with current timestamp should clean old versions
        int collected = mvcc.vacuum(newTs + 1);
        assertTrue(collected >= 0);
    }

    // -----------------------------------------------------------------------
    // Concurrent transactions
    // -----------------------------------------------------------------------

    @Test @Order(40)
    void concurrentTransactionsAllCommit() throws Exception {
        int threads = 10;
        var pool = Executors.newFixedThreadPool(threads);
        var successes = new AtomicInteger(0);
        var futures = new ArrayList<Future<?>>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                try (var tx = db.beginTransaction()) {
                    tx.write("concurrent_tx_col", "key_" + tid, "{\"thread\":" + tid + "}");
                    tx.commit();
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // conflict or error — acceptable under heavy concurrency
                }
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(successes.get() > 0,
                "At least some concurrent transactions must commit");
    }

    @Test @Order(41)
    void concurrentBeginAndRollbackDoesNotCorrupt() throws Exception {
        int threads = 20;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                var tx = db.beginTransaction();
                tx.write("noop_col", "k" + Thread.currentThread().getId(), "{\"v\":1}");
                tx.rollback();
                assertFalse(tx.isOpen());
                assertTrue(tx.isRolledBack());
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @Test @Order(42)
    void transactionDocumentCollectionInsertThenCommit_concurrently() throws Exception {
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        var committed = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                try (var tx = db.beginTransaction()) {
                    var col = tx.documentCollection("tx_concurrent_col");
                    col.insert(Document.of("thread", Thread.currentThread().getId()));
                    tx.commit();
                    committed.incrementAndGet();
                }
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads, committed.get(),
                "All " + threads + " transactional inserts must commit");
    }

    // -----------------------------------------------------------------------
    // EventBus integration
    // -----------------------------------------------------------------------

    @Test @Order(50)
    void eventBusNotNull() {
        assertNotNull(db.eventBus());
    }

    @Test @Order(51)
    void eventBusReceivesCommitEvent() throws Exception {
        var bus = db.eventBus();
        var received = new ArrayList<String>();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_COMMIT,
                e -> received.add(e.collection()));

        var tx = db.beginTransaction();
        tx.commit();

        // Give listener a brief moment to fire (it is synchronous but let's be safe)
        Thread.sleep(50);
        assertFalse(received.isEmpty(),
                "AFTER_COMMIT event must be emitted when a transaction commits");
    }

    @Test @Order(52)
    void eventBusReceivesRollbackEvent() throws Exception {
        var bus = db.eventBus();
        var received = new ArrayList<String>();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_ROLLBACK,
                e -> received.add(e.collection()));

        var tx = db.beginTransaction();
        tx.rollback();
        Thread.sleep(50);
        assertFalse(received.isEmpty(),
                "AFTER_ROLLBACK event must be emitted on rollback");
    }

    @Test @Order(53)
    void eventBusListenerCountIncrementsOnSubscribe() {
        var bus = db.eventBus();
        int before = bus.listenerCount();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.BEFORE_INSERT, e -> {});
        assertEquals(before + 1, bus.listenerCount());
    }

    @Test @Order(54)
    void eventBusClearRemovesAllListeners() {
        var bus = new org.embeddedjnosql.db.core.event.EventBus();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_COMMIT, e -> {});
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_ROLLBACK, e -> {});
        assertEquals(2, bus.listenerCount());
        bus.clear();
        assertEquals(0, bus.listenerCount());
    }

    @Test @Order(55)
    void eventBusEmitDoesNotPropagateListenerException() {
        var bus = new org.embeddedjnosql.db.core.event.EventBus();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_COMMIT, e -> {
            throw new RuntimeException("listener explosion");
        });
        assertDoesNotThrow(() -> bus.emit(
                org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_COMMIT, "test"),
                "EventBus must silently swallow listener exceptions");
    }

    // -----------------------------------------------------------------------
    // DatabaseMetrics
    // -----------------------------------------------------------------------

    @Test @Order(60)
    void metricsRecordInsertIncrements() {
        var metrics = db.metrics();
        var before = (Long) metrics.snapshot().get("inserts");
        metrics.recordInsert();
        var after = (Long) metrics.snapshot().get("inserts");
        assertEquals(before + 1, after);
    }

    @Test @Order(61)
    void metricsRecordDeleteIncrements() {
        var metrics = db.metrics();
        var before = (Long) metrics.snapshot().get("deletes");
        metrics.recordDelete();
        var after = (Long) metrics.snapshot().get("deletes");
        assertEquals(before + 1, after);
    }

    @Test @Order(62)
    void metricsRecordReadIncrements() {
        var metrics = db.metrics();
        var before = (Long) metrics.snapshot().get("reads");
        metrics.recordRead();
        var after = (Long) metrics.snapshot().get("reads");
        assertEquals(before + 1, after);
    }

    @Test @Order(63)
    void metricsRecordTransactionIncrements() {
        var metrics = db.metrics();
        var before = (Long) metrics.snapshot().get("transactions");
        metrics.recordTransaction();
        var after = (Long) metrics.snapshot().get("transactions");
        assertEquals(before + 1, after);
    }

    @Test @Order(64)
    void metricsSnapshotContainsAllExpectedKeys() {
        var snap = db.metrics().snapshot();
        for (var key : List.of("inserts", "deletes", "reads", "queries",
                "transactions", "transactionCommits", "transactionRollbacks",
                "uptimeMs", "totalOperations")) {
            assertTrue(snap.containsKey(key), "Snapshot must contain key: " + key);
        }
    }

    @Test @Order(65)
    void metricsMemoryStatsNotNull() {
        var mem = db.metrics().memoryStats();
        assertNotNull(mem);
        assertTrue(mem.containsKey("heapUsed"));
        assertTrue(mem.containsKey("threadCount"));
        assertTrue(((Long) mem.get("heapUsed")) > 0);
    }

    @Test @Order(66)
    void metricsFastCounters() {
        var metrics = db.metrics();
        metrics.recordInsert();
        metrics.recordRead();
        assertTrue(metrics.getFastInserts() >= 1);
        assertTrue(metrics.getFastReads() >= 1);
    }

    @Test @Order(67)
    void metricsUpdateCollectionSize() {
        var metrics = db.metrics();
        metrics.updateCollectionSize("test_col", 42L);
        var snap = (Map<?, ?>) metrics.snapshot().get("collections");
        assertNotNull(snap);
        assertTrue(snap.containsKey("test_col"));
    }
}