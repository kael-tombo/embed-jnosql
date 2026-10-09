package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void commitPersistsData() {
        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
            tx.commit();
        }

        var col = db.documentCollection("users");
        assertEquals(1, col.count());
    }

    @Test
    void rollbackDiscardsData() {
        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
            tx.rollback();
        }

        var col = db.documentCollection("users");
        assertEquals(0, col.count());
    }

    @Test
    void autoRollbackOnClose() {
        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
        }

        var col = db.documentCollection("users");
        assertEquals(0, col.count());
    }

    @Test
    void commitThenRollbackThrows() {
        var tx = db.beginTransaction();
        var col = tx.documentCollection("users");
        col.insert(Document.of("name", "Alice"));
        tx.commit();

        assertThrows(IllegalStateException.class, tx::rollback);
    }

    @Test
    void transactionStatus() {
        var tx = db.beginTransaction();
        assertFalse(tx.isCommitted());
        assertFalse(tx.isRolledBack());

        tx.commit();
        assertTrue(tx.isCommitted());
        assertFalse(tx.isRolledBack());
    }

    @Test
    void multipleOperationsInTransaction() {
        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
            col.insert(Document.of("name", "Bob"));
            col.insert(Document.of("name", "Charlie"));
            tx.commit();
        }

        var col = db.documentCollection("users");
        assertEquals(3, col.count());
    }

    @Test
    void deleteInTransaction() {
        var col = db.documentCollection("users");
        var doc = col.insert(Document.of("name", "Alice"));

        try (var tx = db.beginTransaction()) {
            var txCol = tx.documentCollection("users");
            txCol.deleteById(doc.id());
            tx.commit();
        }

        assertEquals(0, col.count());
    }

    @Test
    void readYourOwnWritesWithinTransaction() {
        try (var tx = db.beginTransaction()) {
            var txCol = tx.documentCollection("users");
            var doc = txCol.insert(Document.of("name", "Bob").add("role", "admin"));
            assertNotNull(doc.id());

            // Read-your-own-writes via findById
            var found = txCol.findById(doc.id());
            assertNotNull(found, "Document should be readable inside transaction before commit");
            assertEquals("Bob", found.get("name"));

            // Read-your-own-writes via findAll
            var all = txCol.findAll();
            assertEquals(1, all.size());
            assertEquals("Bob", all.get(0).get("name"));

            tx.commit();
        }

        var col = db.documentCollection("users");
        assertEquals(1, col.count());
    }

    @Test
    void transactionalUpdateAndRollback() {
        var col = db.documentCollection("items");
        var doc = col.insert(Document.of("name", "OriginalName").add("qty", 10));

        try (var tx = db.beginTransaction()) {
            var txCol = tx.documentCollection("items");
            var txDoc = txCol.findById(doc.id());
            assertNotNull(txDoc);

            txCol.update(txDoc.add("name", "UpdatedInTx").add("qty", 99));

            // Visible inside transaction
            var modifiedInTx = txCol.findById(doc.id());
            assertNotNull(modifiedInTx);
            assertEquals("UpdatedInTx", modifiedInTx.get("name"));
            assertEquals(Integer.valueOf(99), modifiedInTx.<Integer>get("qty"));

            // Outside transaction, storage engine still has original
            var outside = col.findById(doc.id());
            assertEquals("OriginalName", outside.get("name"));

            tx.rollback();
        }

        // After rollback, original remains
        var afterRollback = col.findById(doc.id());
        assertEquals("OriginalName", afterRollback.get("name"));
        assertEquals(Integer.valueOf(10), afterRollback.<Integer>get("qty"));
    }

    @Test
    void transactionalDeleteAndRollback() {
        var col = db.documentCollection("items");
        var doc = col.insert(Document.of("title", "ItemToDelete"));

        try (var tx = db.beginTransaction()) {
            var txCol = tx.documentCollection("items");
            assertTrue(txCol.deleteById(doc.id()));

            // Inside transaction, it appears deleted
            assertNull(txCol.findById(doc.id()));
            assertEquals(0, txCol.findAll().size());

            tx.rollback();
        }

        // After rollback, item is still present
        assertNotNull(col.findById(doc.id()));
        assertEquals(1, col.count());
    }
}
