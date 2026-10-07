package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType;
import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The propagation matrix the transaction manager's contract advertises, pinned by test:
 * REQUIRED joins (covered by the parity suite), SUPPORTS runs non-transactionally when none
 * exists and inside one when one is active, MANDATORY refuses to run without one,
 * NOT_SUPPORTED suspends the active transaction so writes bypass it entirely, and NESTED is
 * rejected because the MVCC manager has no savepoints.
 */
class EmbedTxPropagationTest {

    private EmbedJNoSQL proxiedDb() {
        EmbedJNoSQL db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .autoFlush(false)
                .buildConfig());
        return (EmbedJNoSQL) new EmbedJNoSQLTxRoutingPostProcessor(true)
                .postProcessAfterInitialization(db, "db");
    }

    @Test
    void supportsRunsNonTransactionallyWithoutOneAndSeesWritesInsideOne() {
        EmbedJNoSQL db = proxiedDb();
        try {
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(db);
            TransactionTemplate supports = new TransactionTemplate(tm);
            supports.setPropagationBehavior(TransactionDefinition.PROPAGATION_SUPPORTS);

            // no active transaction: SUPPORTS executes non-transactionally and commits directly
            supports.executeWithoutResult(s ->
                    db.documentCollection("prop_tx").insert(
                            Document.fromJson("{\"id\":\"direct\",\"v\":\"1\"}")));
            assertTrue(db.documentCollection("prop_tx").findById("direct") != null,
                    "SUPPORTS without a transaction must write straight through");

            // with an active transaction: SUPPORTS joins it — its staged write must roll back with it
            TransactionTemplate required = new TransactionTemplate(tm);
            try {
                required.executeWithoutResult(s1 -> supports.executeWithoutResult(s2 -> {
                    assertTrue(TransactionSynchronizationManager.hasResource(
                                    EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)),
                            "SUPPORTS must join the active transaction");
                    db.documentCollection("prop_tx").insert(
                            Document.fromJson("{\"id\":\"joined\",\"v\":\"1\"}"));
                    throw new IllegalStateException("boom");
                }));
                throw new AssertionError("expected boom");
            } catch (IllegalStateException e) {
                assertEquals("boom", e.getMessage());
            }
            assertTrue(db.documentCollection("prop_tx").findById("joined") == null,
                    "the joined SUPPORTS write must roll back with the outer transaction");
        } finally {
            ((EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)).close();
        }
    }

    @Test
    void mandatoryRefusesToRunWithoutATransaction() {
        EmbedJNoSQL db = proxiedDb();
        try {
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(db);
            TransactionTemplate mandatory = new TransactionTemplate(tm);
            mandatory.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);

            // Spring's canonical MANDATORY violation: IllegalTransactionStateException (a
            // TransactionUsageException subclass) — thrown by APTM validates as MANDATORY before doBegin
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () ->
                    mandatory.executeWithoutResult(s -> { }),
                    "MANDATORY without an active transaction must fail");

            // inside a transaction it runs normally
            new TransactionTemplate(tm).executeWithoutResult(s ->
                    mandatory.executeWithoutResult(inner ->
                            db.documentCollection("prop_tx").insert(
                                    Document.fromJson("{\"id\":\"mand\",\"v\":\"1\"}"))));
            assertFalse(db.documentCollection("prop_tx").findById("mand") == null,
                    "MANDATORY inside a transaction must run and commit with it");
        } finally {
            ((EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)).close();
        }
    }

    @Test
    void notSupportedSuspendsTheActiveTransaction() {
        EmbedJNoSQL db = proxiedDb();
        try {
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(db);
            TransactionTemplate outer = new TransactionTemplate(tm);
            TransactionTemplate notSupported = new TransactionTemplate(tm);
            notSupported.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

            try {
                outer.executeWithoutResult(s1 -> {
                    db.documentCollection("prop_tx").insert(
                            Document.fromJson("{\"id\":\"outer\",\"v\":\"1\"}"));
                    notSupported.executeWithoutResult(s2 -> {
                        assertFalse(TransactionSynchronizationManager.hasResource(
                                        EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)),
                                "NOT_SUPPORTED must suspend the bound transaction");
                        // outside the suspended transaction the staged outer write is invisible
                        // (staged, not committed) and the write here goes straight to the store
                        assertTrue(db.documentCollection("prop_tx").findById("outer") == null,
                                "the suspended outer write must not be visible");
                        db.documentCollection("prop_tx").insert(
                                Document.fromJson("{\"id\":\"bypass\",\"v\":\"1\"}"));
                    });
                    // resumed: the outer staged write is visible again, the bypass is NOT (snapshot)
                    assertTrue(db.documentCollection("prop_tx").findById("outer") != null,
                            "the outer transaction must be resumed with its staged state");
                    db.documentCollection("prop_tx").insert(
                            Document.fromJson("{\"id\":\"bypass2\",\"v\":\"1\"}"));
                    throw new IllegalStateException("boom");
                });
                throw new AssertionError("expected the outer transaction to roll back");
            } catch (IllegalStateException e) {
                assertEquals("boom", e.getMessage());
            }

            // outer rolled back; the NOT_SUPPORTED write committed independently (like REQUIRES_NEW
            // semantics minus the new tx — it executed non-transactionally and persisted directly)
            assertTrue(db.documentCollection("prop_tx").findById("bypass") != null,
                    "the non-transactional write must have committed despite the outer rollback");
            assertTrue(db.documentCollection("prop_tx").findById("outer") == null,
                    "the outer rollback must discard its staged write");
        } finally {
            ((EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)).close();
        }
    }

    @Test
    void nestedIsRejected() {
        EmbedJNoSQL db = proxiedDb();
        try {
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(db);
            TransactionTemplate nested = new TransactionTemplate(tm);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

            // without an outer transaction NESTED still begins a new one through doBegin,
            // which rejects it up front
            assertThrows(TransactionUsageException.class, () ->
                    nested.executeWithoutResult(s -> { }),
                    "NESTED must be rejected (no MVCC savepoints)");
        } finally {
            ((EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)).close();
        }
    }

    @Test
    void commitTimeConflictSurfacesAsUnexpectedRollback() {
        EmbedJNoSQL db = proxiedDb();
        try {
            EmbedJNoSQL raw = (EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db);
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(db);

            // Transaction A reads then stalls before committing; B commits the same key first;
            // A's commit must fail loudly, not silently overwrite.
            TransactionTemplate a = new TransactionTemplate(tm);
            // hold A's staged write until B has committed: begin A manually via a template that
            // blocks at commit — emulate by starting A, committing B through the raw db, then
            // committing A through its own template call. Simplest honest version: two templates,
            // the outer one committing AFTER an interleaved direct commit of the same key.
            TransactionTemplate outer = new TransactionTemplate(tm);
            try {
                outer.executeWithoutResult(s -> {
                    db.documentCollection("prop_tx").insert(
                            Document.fromJson("{\"id\":\"contended\",\"v\":\"fromA\"}"));
                    // B bypasses Spring entirely (a second MVCC client) and commits first
                    var other = raw.beginTransaction();
                    other.write("prop_tx", "contended",
                            Document.fromJson("{\"id\":\"contended\",\"v\":\"fromB\"}").toJson());
                    other.commit();
                });
                throw new AssertionError("the conflicting commit must fail");
            } catch (UnexpectedRollbackException expected) {
                // APTM turns the MVCC conflict into UnexpectedRollbackException — the documented
                // contract: the caller sees the truth instead of a silent lost update
            }

            assertEquals("fromB", raw.documentCollection("prop_tx").findById("contended").get("v"),
                    "first committer wins; A's write must not overwrite B's");

            // APTM calls processRollback after the failed commit; the MVCC transaction is already
            // closed by that path, and "first committer wins" above is the DURABLE assertion here.
        } finally {
            ((EmbedJNoSQL) EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db)).close();
        }
    }
}
