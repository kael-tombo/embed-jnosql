package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Thread-bound holder for the embed-jnosql transaction that backs the current Spring
 * transaction, keyed by the {@link EmbedJNoSQL} instance it was opened against. This mirrors
 * how Spring's JDBC support binds a {@code ConnectionHolder} to the {@code DataSource} key:
 * the routing layer (the transaction-aware {@code EmbedJNoSQL} proxy and the
 * {@link EmbedJNoSQLTransactionManager}) consults this holder to decide whether collection
 * access must be staged in the active transaction.
 *
 * <p>Not public API on purpose: everything outside the starter goes through
 * {@code EmbedJNoSQLTransactionManager} and the routed collections.</p>
 */
final class SpringTransactionHolder {

    private SpringTransactionHolder() {
    }

    static void bind(EmbedJNoSQL db, Transaction tx) {
        TransactionSynchronizationManager.bindResource(db, tx);
    }

    static Transaction unbind(EmbedJNoSQL db) {
        if (TransactionSynchronizationManager.hasResource(db)) {
            return (Transaction) TransactionSynchronizationManager.unbindResource(db);
        }
        return null;
    }

    static Transaction active(EmbedJNoSQL db) {
        if (db == null || !TransactionSynchronizationManager.hasResource(db)) {
            return null;
        }
        return (Transaction) TransactionSynchronizationManager.getResource(db);
    }
}
