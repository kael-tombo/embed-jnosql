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
}
