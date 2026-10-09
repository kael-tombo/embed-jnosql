package org.embeddedjnosql.db.demo.demos.mvcctransactions;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

/**
 * Demo 11 — MVCC transactions: staged writes are invisible until commit,
 * rollback discards every staged change, and read-your-own-writes holds
 * inside the transaction.
 */
public class MvccTransactionDemo {

    public record Result(String visibleInsideTx, boolean invisibleOutsideBeforeCommit,
                         boolean visibleOutsideAfterCommit, boolean rollbackDiscarded) {}

    public static Result runDemo(EmbedJNoSQL db) {
        var orders = db.documentCollection("orders");
        orders.insert(new Document().id("o-1").add("status", "NEW"));

        boolean invisibleOutside = true;
        String insideValue = null;

        // 1. Committed transaction: staged write hidden outside, visible inside, visible after
        try (Transaction tx = db.beginTransaction()) {
            tx.write("orders", "o-2", new Document().id("o-2").add("status", "DRAFT").toJson());
            // Read-your-own-writes inside the transaction
            insideValue = tx.read("orders", "o-2");
            // Outside world cannot see the staged document yet
            invisibleOutside = orders.findById("o-2") == null;
            tx.commit();
        }
        boolean visibleAfter = orders.findById("o-2") != null;

        // 2. Rolled-back transaction: nothing lands
        boolean rollbackDiscarded;
        try (Transaction tx = db.beginTransaction()) {
            tx.write("orders", "o-3", new Document().id("o-3").add("status", "GHOST").toJson());
            tx.rollback();
            rollbackDiscarded = true;
        }
        rollbackDiscarded = rollbackDiscarded && orders.findById("o-3") == null;

        Result r = new Result(insideValue, invisibleOutside, visibleAfter, rollbackDiscarded);
        if (r.visibleInsideTx() == null || !r.invisibleOutsideBeforeCommit()
                || !r.visibleOutsideAfterCommit() || !r.rollbackDiscarded()) {
            throw new AssertionError("Unexpected transaction results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== MVCC transaction demo ==");
            System.out.println("read-own-writes    : " + r.visibleInsideTx());
            System.out.println("hidden pre-commit  : " + r.invisibleOutsideBeforeCommit());
            System.out.println("visible post-commit: " + r.visibleOutsideAfterCommit());
            System.out.println("rollback discarded : " + r.rollbackDiscarded());
        }
    }
}
