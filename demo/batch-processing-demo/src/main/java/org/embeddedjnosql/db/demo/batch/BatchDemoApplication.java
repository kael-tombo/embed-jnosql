package org.embeddedjnosql.db.demo.batch;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.batch.model.FinancialTransaction;
import org.embeddedjnosql.db.demo.batch.service.BatchIngestionService;
import org.embeddedjnosql.db.demo.batch.service.BatchProcessingResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executable CLI demonstration of high-throughput batch processing with EmbedJNoSQL.
 */
public class BatchDemoApplication {

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("   EmbedJNoSQL Demo: High-Throughput Batch Processing       ");
        System.out.println("=========================================================");

        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            BatchIngestionService service = new BatchIngestionService(db);

            // 1. Generate 10,000 synthetic transactions
            int totalRecords = 10000;
            System.out.printf("Generating %,d synthetic financial transactions...\n", totalRecords);
            List<FinancialTransaction> txs = new ArrayList<>(totalRecords);
            Map<String, Double> balances = new HashMap<>();

            for (int i = 1; i <= totalRecords; i++) {
                String id = "TXN-" + String.format("%07d", i);
                String acc = "ACC-" + (1000 + (i % 200));
                double amt = 10.0 + (i % 500);
                txs.add(FinancialTransaction.of(id, acc, "ACC-SETTLEMENT", amt, i % 2 == 0 ? "CREDIT" : "DEBIT"));
                balances.put(acc, 5000.0 + amt);
            }

            // 2. Chunked Batch Ingestion (500 per chunk)
            System.out.println("\n[1/3] Executing Chunked Batch Ingestion (chunk size: 500)...");
            BatchProcessingResult chunkedResult = service.ingestInChunks(txs, 500);
            System.out.printf("  Processed: %,d docs in %d ms (%.1f ops/sec)\n",
                    chunkedResult.totalProcessed(), chunkedResult.durationMs(), chunkedResult.throughputOpsPerSec());

            // 3. Bulk Balances Key-Value Ingestion
            System.out.println("\n[2/3] Executing Bulk Key-Value Store Loading with TTL...");
            BatchProcessingResult kvResult = service.bulkLoadBalances(balances, 3600);
            System.out.printf("  Loaded: %,d balances in %d ms (%.1f ops/sec)\n",
                    kvResult.totalProcessed(), kvResult.durationMs(), kvResult.throughputOpsPerSec());

            // 4. Fault-Injection Rollback Demo
            System.out.println("\n[3/3] Executing Fault Injection Test (Atomic Batch Rollback)...");
            List<FinancialTransaction> faultBatch = List.of(
                    FinancialTransaction.of("F-1", "A1", "A2", 100.0, "TRANSFER"),
                    FinancialTransaction.of("F-2", "A1", "A2", 200.0, "TRANSFER"),
                    FinancialTransaction.of("F-3", "A1", "A2", 300.0, "TRANSFER")
            );
            long beforeCount = service.countTotalTransactions();
            BatchProcessingResult faultResult = service.ingestBatchWithFaultInjection(faultBatch, 1);
            long afterCount = service.countTotalTransactions();

            System.out.println("  Result: " + faultResult.message());
            System.out.println("  Database count before: " + beforeCount + ", after: " + afterCount);
            if (beforeCount == afterCount) {
                System.out.println("  [VERIFIED] Complete atomic rollback achieved! Zero orphaned records.");
            }

            System.out.println("\n=========================================================");
            System.out.println("   Batch Processing Demo Completed Successfully!         ");
            System.out.println("=========================================================");
        }
    }
}
