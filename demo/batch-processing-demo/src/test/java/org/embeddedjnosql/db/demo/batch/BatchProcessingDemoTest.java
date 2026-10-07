package org.embeddedjnosql.db.demo.batch;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.batch.model.FinancialTransaction;
import org.embeddedjnosql.db.demo.batch.service.BatchIngestionService;
import org.embeddedjnosql.db.demo.batch.service.BatchProcessingResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite validating atomic batch insertion, fault rollback,
 * chunking, and Redis-style batch queueing.
 */
public class BatchProcessingDemoTest {

    private EmbedJNoSQL db;
    private BatchIngestionService service;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.inMemory();
        service = new BatchIngestionService(db);
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Test
    @DisplayName("BATCH-01: Atomic batch ingestion commits all records")
    void testAtomicBatchIngestion() {
        int count = 2000;
        List<FinancialTransaction> txs = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            txs.add(FinancialTransaction.of("TX-" + i, "ACC-" + i, "ACC-TARGET", 100.0 + i, "TRANSFER"));
        }

        BatchProcessingResult result = service.ingestBatchAtomic(txs);
        assertFalse(result.rolledBack());
        assertEquals(count, result.totalProcessed());
        assertEquals(count, service.countTotalTransactions());
        assertTrue(result.throughputOpsPerSec() > 0);
    }

    @Test
    @DisplayName("BATCH-02: Fault injection triggers atomic rollback with zero orphaned records")
    void testAtomicBatchRollbackOnFault() {
        // Seed 10 existing records
        List<FinancialTransaction> seed = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            seed.add(FinancialTransaction.of("SEED-" + i, "ACC-1", "ACC-2", 50.0, "CREDIT"));
        }
        service.ingestBatchAtomic(seed);
        assertEquals(10, service.countTotalTransactions());

        // Attempt batch of 100 items with failure injected at index 50
        List<FinancialTransaction> failingBatch = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            failingBatch.add(FinancialTransaction.of("FAIL-" + i, "ACC-X", "ACC-Y", 10.0, "DEBIT"));
        }

        BatchProcessingResult result = service.ingestBatchWithFaultInjection(failingBatch, 50);
        assertTrue(result.rolledBack(), "Batch with fault injection must be rolled back");
        assertEquals(10, service.countTotalTransactions(), "Collection count must remain exactly as before batch");

        // Verify none of the FAIL- items exist
        assertNull(db.documentCollection("financial_transactions").findById("FAIL-1"));
        assertNull(db.documentCollection("financial_transactions").findById("FAIL-49"));
    }

    @Test
    @DisplayName("BATCH-03: Chunked ingestion processes large dataset in sub-batches")
    void testChunkedIngestion() {
        int count = 5000;
        List<FinancialTransaction> txs = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            txs.add(FinancialTransaction.of("CHUNK-" + i, "ACC-" + (i % 10), "ACC-DST", 25.0, "FEE"));
        }

        BatchProcessingResult result = service.ingestInChunks(txs, 500);
        assertFalse(result.rolledBack());
        assertEquals(count, result.totalProcessed());
        assertEquals(count, service.countTotalTransactions());
    }

    @Test
    @DisplayName("BATCH-04: Bulk Key-Value balances loading with TTL")
    void testBulkBalancesLoading() {
        Map<String, Double> balances = Map.of(
                "ACC-100", 1500.50,
                "ACC-101", 2300.00,
                "ACC-102", 950.25,
                "ACC-103", 12000.00
        );

        BatchProcessingResult result = service.bulkLoadBalances(balances, 60);
        assertEquals(4, result.totalProcessed());
        var kv = db.keyValueBucket("account_balances");
        assertEquals("1500.5", kv.get("ACC-100"));
        assertEquals("12000.0", kv.get("ACC-103"));
    }

    @Test
    @DisplayName("BATCH-05: Redis-style batch queueing using RPUSH")
    void testBatchQueueing() {
        List<String> items = List.of("JOB-101", "JOB-102", "JOB-103", "JOB-104", "JOB-105");
        BatchProcessingResult result = service.batchQueueTransactions("settlement_queue", items);
        assertEquals(5, result.totalProcessed());

        var queue = db.listBucket("settlement_queue");
        assertEquals(5, queue.llen("settlement_queue_key"));
        assertEquals("JOB-101", queue.lpop("settlement_queue_key"));
        assertEquals(4, queue.llen("settlement_queue_key"));
    }
}
