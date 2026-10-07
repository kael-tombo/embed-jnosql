package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * The Spring {@link org.springframework.transaction.PlatformTransactionManager} for
 * embed-jnosql — the piece that makes plain {@code @Transactional} work against the embedded
 * database, the same role Spring's {@code DataSourceTransactionManager} plays for H2.
 *
 * <p>Each Spring transaction opens one MVCC {@link Transaction} via
 * {@link EmbedJNoSQL#beginTransaction()} and binds it to the thread under the owning
 * {@code EmbedJNoSQL} key. While it is bound, every {@code documentCollection()} access
 * through the auto-configured (routed) database bean stages its mutations in that
 * transaction; commit applies them atomically, rollback discards them.</p>
 *
 * <p>Propagation: {@code REQUIRED}, {@code SUPPORTS}, {@code MANDATORY}, {@code REQUIRES_NEW}
 * (suspend/resume of the outer MVCC transaction), and {@code NOT_SUPPORTED} are supported.
 * {@code NESTED} is rejected — the MVCC manager has no savepoints. Isolation levels are
 * served by the MVCC snapshot model (snapshot isolation) and are therefore not individually
 * configurable; timeouts map onto the transaction's own timeout.</p>
 *
 * <p>Conflict handling: if the MVCC manager detects a write-write conflict at commit time,
 * the transaction has already rolled itself back — this manager surfaces that as
 * {@link UnexpectedRollbackException} so callers (and {@code @Transactional}) see the truth.</p>
 */
public class EmbedJNoSQLTransactionManager extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 1L;

    private final EmbedJNoSQL db;
    /**
     * The object the tx-routed {@code EmbedJNoSQL} proxy keys its lookups on: Spring's
     * {@code MethodInvocation.getThis()} is the proxy's TARGET (the raw database bean), not the
     * proxy itself, so the transaction manager must bind resources under the same raw instance
     * or the routing layer would never see the active transaction.
     */
    private final EmbedJNoSQL bindKey;

    public EmbedJNoSQLTransactionManager(EmbedJNoSQL db) {
        this.db = db;
        this.bindKey = unwrapRoutingTarget(db);
    }

    public EmbedJNoSQL getDatabase() {
        return db;
    }

    /**
     * Unwraps the tx-routing proxy (if any) down to the raw {@link EmbedJNoSQL} target so
     * resource binding uses the exact instance the routing interceptor consults.
     */
    static EmbedJNoSQL unwrapRoutingTarget(EmbedJNoSQL db) {
        if (db instanceof org.springframework.aop.framework.Advised advised) {
            try {
                Object target = advised.getTargetSource().getTarget();
                if (target instanceof EmbedJNoSQL raw) {
                    return raw;
                }
            } catch (Exception ignored) {
                // fall through to identity
            }
        }
        return db;
    }

    /** Spring transaction object carrying the thread's MVCC transaction. */
    private static class EmbedTransactionObject {
        final EmbedJNoSQL db;
        Transaction transaction;

        EmbedTransactionObject(EmbedJNoSQL db) {
            this.db = db;
        }
    }

    @Override
    protected Object doGetTransaction() throws TransactionException {
        EmbedTransactionObject txObject = new EmbedTransactionObject(db);
        // Reflect the thread-bound MVCC transaction, exactly like DataSourceTransactionManager
        // reads its ConnectionHolder: without this, isExistingTransaction() always sees "none",
        // so REQUIRED never joins and REQUIRES_NEW never suspends the outer transaction — the
        // next doBegin would then hit "Already value bound to thread".
        txObject.transaction = SpringTransactionHolder.active(bindKey);
        return txObject;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((EmbedTransactionObject) transaction).transaction != null;
    }

    @Override
    protected void doBegin(Object transactionObject, TransactionDefinition definition)
            throws TransactionException {
        EmbedTransactionObject txObject = (EmbedTransactionObject) transactionObject;

        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
            throw new org.springframework.transaction.TransactionUsageException(
                    "PROPAGATION_NESTED is not supported by EmbedJNoSQLTransactionManager: "
                            + "the MVCC manager has no savepoints");
        }

        Transaction tx = db.beginTransaction();
        if (definition.getTimeout() > 0) {
            tx.timeoutMs(definition.getTimeout() * 1000L);
        }
        txObject.transaction = tx;
        SpringTransactionHolder.bind(bindKey, tx);
    }

    @Override
    protected Object doSuspend(Object transactionObject) {
        EmbedTransactionObject txObject = (EmbedTransactionObject) transactionObject;
        Transaction suspended = SpringTransactionHolder.unbind(bindKey);
        txObject.transaction = null;
        return suspended;
    }

    @Override
    protected void doResume(Object transactionObject, Object suspendedResources) {
        EmbedTransactionObject txObject = (EmbedTransactionObject) transactionObject;
        txObject.transaction = (Transaction) suspendedResources;
        if (suspendedResources != null) {
            SpringTransactionHolder.bind(bindKey, (Transaction) suspendedResources);
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        EmbedTransactionObject txObject = (EmbedTransactionObject) status.getTransaction();
        Transaction tx = txObject.transaction;
        if (tx == null) {
            return;
        }
        try {
            tx.commit();
        } catch (RuntimeException e) {
            throw new org.springframework.transaction.TransactionSystemException(
                    "Could not commit embed-jnosql transaction " + tx.id(), e);
        }
        if (tx.isRolledBack()) {
            // MVCC write-write conflict (or apply failure): the transaction rolled itself
            // back — surface it instead of pretending the commit succeeded.
            throw new UnexpectedRollbackException(
                    "embed-jnosql transaction " + tx.id() + " rolled back at commit (write-write conflict)");
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        EmbedTransactionObject txObject = (EmbedTransactionObject) status.getTransaction();
        Transaction tx = txObject.transaction;
        if (tx != null && tx.isOpen()) {
            tx.rollback();
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transactionObject) {
        EmbedTransactionObject txObject = (EmbedTransactionObject) transactionObject;
        Transaction tx = txObject.transaction;
        txObject.transaction = null;
        SpringTransactionHolder.unbind(bindKey);
        if (tx != null) {
            // AutoCloseable hygiene: releases the MVCC snapshot and fires internal cleanup.
            // Best-effort: commit/rollback already settled the outcome.
            try {
                tx.close();
            } catch (RuntimeException ignored) {
                // no-op
            }
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        // Nothing to flag on the MVCC transaction itself: rollback is decided by the
        // framework at completion time (doRollback). The thread binding stays until then.
    }
}
