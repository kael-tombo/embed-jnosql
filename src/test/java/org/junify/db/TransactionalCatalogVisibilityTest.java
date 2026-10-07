package org.junify.db;

import org.junify.db.adapter.jnosql.Entity;
import org.junify.db.adapter.jnosql.Id;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.nosql.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for <b>R-59</b>: collections written through a <em>transaction</em> were
 * absent from the database catalog.
 *
 * <p>A transaction hands out a {@code TransactionalCollection} that writes straight to the
 * storage engine and never registers itself in {@code JunifyDB}'s in-memory {@code collections}
 * map. So after a successful commit the data existed and was readable <em>by name</em>, but
 * {@code getCollectionNames()} did not list it.</p>
 *
 * <p>The defect was latent until reads began resolving through a non-creating path: an
 * {@code @Entity} is persisted inside a transaction and then read back by name, and the read
 * returned nothing because the catalog was wrong. It also meant committed data was invisible
 * to backups and to the console's collection list.</p>
 */
@DisplayName("Collections written in a transaction are visible to the catalog")
class TransactionalCatalogVisibilityTest {

    private JunifyDB db;

    @BeforeEach
    void setUp() {
        db = JunifyDB.create(JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    @Test
    @DisplayName("a committed transactional write appears in the catalog and is queryable by name")
    void committedTransactionalWriteIsCatalogued() {
        try (var tx = db.beginTransaction()) {
            tx.documentCollection("invoices").insert(
                    new Document().id("inv-1").add("customer_id", "CUST-1").add("amount", 100.0));
            tx.commit();
        }

        assertTrue(db.getCollectionNames().contains("invoices"),
                "a committed transactional write must be listed by the catalog, otherwise "
                        + "backups and the console all miss it: " + db.getCollectionNames());

        assertEquals(1, db.documentCollection("invoices").findAll().size(),
                "the committed row must be readable through the document API by name");
    }

    @Test
    @DisplayName("registration happens once, and reads of the committed data work repeatedly")
    void repeatedReadsWorkAfterCommit() {
        try (var tx = db.beginTransaction()) {
            tx.documentCollection("orders_tx").insert(new Document().id("o1").add("total", 42));
            tx.commit();
        }

        assertTrue(db.getCollectionNames().contains("orders_tx"), db.getCollectionNames().toString());
        assertEquals(1, db.documentCollection("orders_tx").findAll().size());
        // A second read must not require the collection to be re-registered by the read itself.
        assertEquals(1, db.documentCollection("orders_tx").findAll().size());
    }

    @Test
    @DisplayName("a rolled-back transaction leaves the catalog untouched")
    void rolledBackTransactionRegistersNothing() {
        int before = db.getCollectionNames().size();

        try (var tx = db.beginTransaction()) {
            tx.documentCollection("never_committed").insert(new Document().id("x1").add("v", 1));
            tx.rollback();
        }

        assertFalse(db.getCollectionNames().contains("never_committed"),
                "a rolled-back transaction holds no data, so its collection must not be catalogued");
        assertEquals(before, db.getCollectionNames().size(), "catalog size must be unchanged");
    }

    @Entity("typo_table")
    static class TypoTable {
        @Id
        private String id;
    }

    @Test
    @DisplayName("a never-written collection is not invented by a query")
    void queriesStillNeverCreateCollections() {
        int before = db.getCollectionNames().size();

        assertTrue(db.from(TypoTable.class).list().isEmpty(),
                "a query against a collection nobody wrote must be an empty result, not a create");

        assertEquals(before, db.getCollectionNames().size(),
                "the query must not have added anything to the catalog");
    }
}
