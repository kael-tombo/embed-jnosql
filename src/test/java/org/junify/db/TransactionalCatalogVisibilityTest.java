package org.junify.db;

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
 * <p>The defect was latent until R-48 made SQL reads resolve through a non-creating path: the
 * JPA/annotation demo persists an {@code @Entity @Table(name = "invoices")} inside a transaction
 * and then runs {@code SELECT ... FROM invoices}, which began failing with
 * {@code SqlUnknownTableException} — the read was correct, the catalog was wrong. It also meant
 * committed data was invisible to backups and to the console's collection list.</p>
 */
@DisplayName("Collections written in a transaction are visible to the catalog (R-59)")
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
    @DisplayName("R-59: a committed transactional write appears in the catalog and is queryable by SQL")
    void committedTransactionalWriteIsCatalogued() {
        try (var tx = db.beginTransaction()) {
            tx.documentCollection("invoices").insert(
                    new Document().id("inv-1").add("customer_id", "CUST-1").add("amount", 100.0));
            tx.commit();
        }

        assertTrue(db.getCollectionNames().contains("invoices"),
                "a committed transactional write must be listed by the catalog, otherwise SQL, "
                        + "backups and the console all miss it: " + db.getCollectionNames());

        var result = db.sql("SELECT amount FROM invoices");
        assertEquals(1, result.getRows().size(),
                "the committed row must be readable through SQL, not a 'table does not exist' error");
    }

    @Test
    @DisplayName("R-59: registration happens once, and reads of the committed data work repeatedly")
    void repeatedReadsWorkAfterCommit() {
        try (var tx = db.beginTransaction()) {
            tx.documentCollection("orders_tx").insert(new Document().id("o1").add("total", 42));
            tx.commit();
        }

        assertTrue(db.getCollectionNames().contains("orders_tx"), db.getCollectionNames().toString());
        assertEquals(1, db.sql("SELECT total FROM orders_tx").getRows().size());
        // A second read must not require the collection to be re-registered by the read itself.
        assertEquals(1, db.sql("SELECT total FROM orders_tx").getRows().size());
    }

    @Test
    @DisplayName("R-59: a rolled-back transaction leaves the catalog untouched")
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

    @Test
    @DisplayName("R-59: the R-48 guard still holds — a never-written collection is not invented by a read")
    void readsStillNeverCreateCollections() {
        int before = db.getCollectionNames().size();

        assertThrows(org.junify.db.sql.SqlUnknownTableException.class,
                () -> db.sql("SELECT amount FROM typo_table"),
                "reading a table nobody wrote must still error rather than create it (R-48)");

        assertEquals(before, db.getCollectionNames().size(),
                "the failed read must not have added anything to the catalog");
    }
}
