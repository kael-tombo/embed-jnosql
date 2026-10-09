package org.embeddedjnosql.db.demo.demos.sqltonosqlmigration;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlMigrationDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("join tables denormalize into document aggregates without data loss")
    void runDemo() {
        var r = SqlMigrationDemo.runDemo(db);
        assertNotNull(r.migratedOrder());
        assertEquals(2, r.ordersForAlice());
        assertTrue(r.itemsNested());
        assertTrue(r.totalsMatch());
    }

    @Test
    @DisplayName("a migrated order answers queries that would have needed a SQL JOIN")
    void joinedQueryInOneCollection() {
        SqlMigrationDemo.runDemo(db);
        var orders = db.documentCollection("orders");
        // "Orders for customer Alice over 1000" — one collection, one query
        var hits = orders.find(
                org.embeddedjnosql.db.nosql.document.Query.gt("total", 1000));
        assertEquals(1, hits.size());
        assertEquals("101", hits.get(0).id());
    }
}
