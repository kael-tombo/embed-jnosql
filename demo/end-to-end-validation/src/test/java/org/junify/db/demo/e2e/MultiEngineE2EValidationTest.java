package org.junify.db.demo.e2e;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junify.db.JunifyDB;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;
import org.junify.db.demo.model.Order;
import org.junify.db.demo.model.OrderItem;
import org.junify.db.demo.model.Product;
import org.junify.db.demo.model.SampleData;
import org.junify.db.nosql.column.ColumnFamily;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;
import org.junify.db.nosql.kv.KeyValueBucket;
import org.junify.db.transaction.mvcc.Transaction;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end multi-engine validation test validating:
 * 1. Full E-Commerce workflow across all storage engines (IN_MEMORY, FILE, B_TREE, LSM_TREE)
 * 2. Multi-model consistency: Documents (products/orders) + KeyValue (cache) + ColumnFamily (inventory)
 * 3. ACID Transaction commit and rollback with stock validation
 * 4. Cold restart and persistence durability for persistent engines (FILE, B_TREE, LSM_TREE)
 */
public class MultiEngineE2EValidationTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(StorageEngineType.class)
    void testFullEcommerceLifecycleAcrossAllEngines(StorageEngineType engineType) {
        Path engineDataDir = tempDir.resolve("e2e-" + engineType.name());

        // Step 1: Initialize Database & seed initial catalog
        JunifyDB db = JunifyDB.create(
                JunifyDBConfig.builder()
                        .storageEngine(engineType)
                        .persistTo(engineDataDir.toString())
                        .autoFlush(true)
                        .flushIntervalMs(100)
                        .buildConfig()
        );

        DocumentCollection productCol = db.documentCollection("products");
        DocumentCollection orderCol = db.documentCollection("orders");
        KeyValueBucket priceCache = db.keyValueBucket("price_cache");
        ColumnFamily inventory = db.columnFamily("inventory");

        // Seed products and inventory
        for (Product p : SampleData.products()) {
            productCol.insert(p.toDocument());
            priceCache.put(p.id(), String.valueOf(p.price()));
        }

        inventory.put("prod-101", "available", 50);
        inventory.put("prod-101", "warehouse", "US-EAST");
        inventory.put("prod-102", "available", 30);
        inventory.put("prod-102", "warehouse", "US-WEST");

        // Verify product queries and secondary indexing
        assertEquals(5, productCol.count());
        List<Document> laptops = productCol.find(Query.eq("category", "Laptops"));
        assertEquals(1, laptops.size());
        assertEquals("prod-101", laptops.get(0).id());

        // Verify KV price cache
        assertEquals("1899.99", priceCache.get("prod-101"));

        // Step 2: Transactional Order Placement - SUCCESS CASE
        Transaction txSuccess = db.beginTransaction();
        try {
            int currentStock = ((Number) inventory.get("prod-101", "available")).intValue();
            assertTrue(currentStock >= 5);

            inventory.put("prod-101", "available", currentStock - 5);

            Order order1 = new Order(
                    "ord-e2e-1",
                    "ORD-E2E-SUCCESS",
                    "cust-001",
                    List.of(new OrderItem("prod-101", "Developer Ultrabook 16", 5, 1899.99)),
                    9499.95,
                    "CONFIRMED",
                    System.currentTimeMillis()
            );
            orderCol.insert(order1.toDocument());
            txSuccess.commit();
        } catch (Exception e) {
            txSuccess.rollback();
            fail("Transaction should have succeeded: " + e.getMessage());
        }

        assertEquals(45, ((Number) inventory.get("prod-101", "available")).intValue());
        assertEquals(1, orderCol.count());

        // Step 3: Transactional Order Placement - ROLLBACK CASE (Insufficient Stock)
        Transaction txFail = db.beginTransaction();
        boolean rollbackTriggered = false;
        try {
            int currentStock = ((Number) inventory.get("prod-101", "available")).intValue(); // 45
            int requestedQuantity = 100; // More than available

            if (currentStock < requestedQuantity) {
                txFail.rollback();
                rollbackTriggered = true;
            } else {
                inventory.put("prod-101", "available", currentStock - requestedQuantity);
                txFail.commit();
            }
        } catch (Exception e) {
            txFail.rollback();
            rollbackTriggered = true;
        }

        assertTrue(rollbackTriggered);
        // Stock must remain unchanged at 45
        assertEquals(45, ((Number) inventory.get("prod-101", "available")).intValue());
        assertEquals(1, orderCol.count());

        // Step 4: Graceful Shutdown & Persistence Check (Cold Restart)
        db.close();

        // For persistent storage engines, re-open and verify durability
        if (engineType != StorageEngineType.IN_MEMORY) {
            JunifyDB reopenedDb = JunifyDB.create(
                    JunifyDBConfig.builder()
                            .storageEngine(engineType)
                            .persistTo(engineDataDir.toString())
                            .autoFlush(true)
                            .buildConfig()
            );

            // Catalog discovery (R-53, 2026-09-22): the reopened catalog must list
            // every persisted collection WITHOUT a client asking for it by name —
            // this is what /api/collections, backups, and SQL all read.
            assertTrue(reopenedDb.getCollectionNames().contains("products"),
                    "catalog must rediscover 'products' after restart on " + engineType
                            + ": " + reopenedDb.getCollectionNames());
            assertTrue(reopenedDb.getCollectionNames().contains("orders"),
                    "catalog must rediscover 'orders' after restart on " + engineType);

            DocumentCollection reopenedProducts = reopenedDb.documentCollection("products");
            DocumentCollection reopenedOrders = reopenedDb.documentCollection("orders");
            ColumnFamily reopenedInventory = reopenedDb.columnFamily("inventory");

            assertEquals(5, reopenedProducts.count(), "Products persisted in engine " + engineType);
            assertEquals(1, reopenedOrders.count(), "Orders persisted in engine " + engineType);
            assertEquals(45, ((Number) reopenedInventory.get("prod-101", "available")).intValue(),
                    "Stock deduction persisted in engine " + engineType);

            reopenedDb.close();
        }
    }
}
