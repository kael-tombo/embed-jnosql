package org.embeddedjnosql.db.demo.demos.mvcctransactions;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MvccTransactionDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("commit publishes staged writes; rollback discards them")
    void runDemo() {
        var r = MvccTransactionDemo.runDemo(db);
        assertNotNull(r.visibleInsideTx());
        assertTrue(r.invisibleOutsideBeforeCommit());
        assertTrue(r.visibleOutsideAfterCommit());
        assertTrue(r.rollbackDiscarded());
    }

    @Test
    @DisplayName("a closed transaction rejects further operations")
    void closedTxRejectsWrites() {
        var tx = db.beginTransaction();
        tx.commit();
        assertThrows(IllegalStateException.class,
                () -> tx.write("orders", "x", "{}"));
    }
}
