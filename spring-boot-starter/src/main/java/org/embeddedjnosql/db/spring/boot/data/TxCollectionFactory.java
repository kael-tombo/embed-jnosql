package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Function;

/**
 * Transaction-awareness seam for the Spring Data repository layer: decides, per
 * {@code documentCollection()} resolution, whether the write/read surface must stage in the
 * active Spring-transaction.
 *
 * <p>Preferred acquisition is the routed database bean itself —
 * {@code EmbedJNoSQLTxRoutingPostProcessor} wraps {@code embedJNoSQL} in a proxy at
 * instrumentation time and {@code documentCollection()} then routes through it. But a
 * database reference captured eagerly (e.g. by a {@code FactoryBean} resolved during
 * {@code registerBeanPostProcessors}' type-checking cascade, which finalizes raw singletons
 * <em>before</em> BPPs are registered) never sees the proxy: its cached instance is the bare
 * database, and mutating through it would bypass {@code @Transactional} staging entirely.</p>
 *
 * <p>To make such holders behave identically to routed ones, this factory falls back to a
 * direct check of the thread-bound transaction — the same binding
 * {@link org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTransactionManager} maintains via
 * {@code SpringTransactionHolder} — and snapshots the active one for the whole resolution
 * (query building plus execution) so it cannot race a transaction completing mid-flight.</p>
 *
 * <p>The active transaction is snapshotted once per {@link #collection} call and reused for
 * the whole derived query/build/execute cycle. Doing the lookup again after the cycle would
 * let a viewer/serializer completing its own transaction pick up a different (later)
 * identity, so the repository-side guarantee is: whatever transaction the collection was
 * snapshotted for, that is the transaction the write stages into.</p>
 */
public final class TxCollectionFactory {

    private TxCollectionFactory() {
    }

    /**
     * Resolves {@code collectionName} against {@code db}, staging in the transaction bound by
     * the {@code EmbedJNoSQL}-keyed {@code TransactionSynchronizationManager} when that
     * binding (the proxy-routed database bean or another consumer of the underlying factory
     * seam) is active.
     */
    public static DocumentCollection forDb(EmbedJNoSQL db,
            Function<EmbedJNoSQL, DocumentCollection> rawResolution, String collectionName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.hasResource(db)) {
            return routed(db, rawResolution, collectionName);
        }
        return rawResolution.apply(db);
    }

    private static DocumentCollection routed(EmbedJNoSQL db,
            Function<EmbedJNoSQL, DocumentCollection> rawResolution, String collectionName) {
        Object resource = TransactionSynchronizationManager.getResource(db);
        if (resource instanceof org.embeddedjnosql.db.transaction.mvcc.Transaction tx) {
            return org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTxRoutingPostProcessor
                    .routedCollection(db, collectionName, tx);
        }
        // theoretically unreachable when hasResource held, but fall back to the raw
        // resolution rather than route on something that is not our Transaction
        return rawResolution.apply(db);
    }
}
