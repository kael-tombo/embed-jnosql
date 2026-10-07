package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level verification of the transaction machinery without Spring's AOP: a directly
 * post-processed (proxied) {@link EmbedJNoSQL} plus the {@link EmbedJNoSQLTransactionManager}.
 *
 * <p>Covers the two failure modes this wiring can have, both discovered here:
 * <ol>
 *   <li>the routing interceptor must resolve the active transaction — it keys on the proxy's
 *       RAW TARGET (Spring's {@code MethodInvocation.getThis()} is the target, not the proxy),
 *       while the transaction manager binds resources under that same unwrapped instance;</li>
 *   <li>{@code doGetTransaction} must reflect the thread-bound transaction — otherwise Spring
 *       never detects the existing transaction, REQUIRED never joins, and REQUIRES_NEW never
 *       suspends the outer transaction (the classic DataSourceTransactionManager pattern).</li>
 * </ol></p>
 */
class EmbedTxRoutingTest {

    @Test
    void routingStagesWritesAndRollbackDiscardsThem() {
        EmbedJNoSQL db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .autoFlush(false)
                .buildConfig());
        try {
            EmbedJNoSQL proxied = (EmbedJNoSQL) new EmbedJNoSQLTxRoutingPostProcessor(true)
                    .postProcessAfterInitialization(db, "db");

            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(proxied);
            TransactionTemplate tt = new TransactionTemplate(tm);

            try {
                tt.executeWithoutResult(s -> {
                    DocumentCollection c = proxied.documentCollection("routing_tx");
                    assertInstanceOf(TransactionAwareDocumentCollection.class, c,
                            "collection must be routed while a tx is bound");
                    c.insert(Document.fromJson("{\"id\":\"a\",\"v\":\"1\"}"));
                    assertEquals(1, c.findAll().size(), "read-your-own-writes inside tx");
                    throw new IllegalStateException("force rollback");
                });
                throw new AssertionError("expected the forced exception to propagate");
            } catch (IllegalStateException expected) {
                assertEquals("force rollback", expected.getMessage());
            }

            assertEquals(0, proxied.documentCollection("routing_tx").findAll().size(),
                    "rollback must discard the staged insert");
            assertNull(proxied.documentCollection("routing_tx").findById("a"),
                    "staged key must be gone after rollback");
        } finally {
            db.close();
        }
    }

    @Test
    void requiresNewSurvivesOuterRollback() {
        EmbedJNoSQL db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .autoFlush(false)
                .buildConfig());
        try {
            EmbedJNoSQL proxied = (EmbedJNoSQL) new EmbedJNoSQLTxRoutingPostProcessor(true)
                    .postProcessAfterInitialization(db, "db");
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(proxied);

            TransactionTemplate outer = new TransactionTemplate(tm);
            TransactionTemplate inner = new TransactionTemplate(tm);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            try {
                outer.executeWithoutResult(s1 -> {
                    proxied.documentCollection("routing_tx")
                            .insert(Document.fromJson("{\"id\":\"doomed\",\"v\":\"1\"}"));
                    inner.executeWithoutResult(s2 -> proxied.documentCollection("routing_tx")
                            .insert(Document.fromJson("{\"id\":\"survivor\",\"v\":\"1\"}")));
                    throw new IllegalStateException("outer boom");
                });
                throw new AssertionError("expected outer boom");
            } catch (IllegalStateException e) {
                assertEquals("outer boom", e.getMessage());
            }

            assertNull(proxied.documentCollection("routing_tx").findById("doomed"),
                    "outer rollback must discard");
            assertEquals("1", proxied.documentCollection("routing_tx").findById("survivor").get("v"),
                    "REQUIRES_NEW commit must survive the outer rollback");
        } finally {
            db.close();
        }
    }

    @Test
    void templateResolvesCollectionsPerCallAndRoutesInsideTransaction() {
        EmbedJNoSQL db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .autoFlush(false)
                .buildConfig());
        try {
            EmbedJNoSQL proxied = (EmbedJNoSQL) new EmbedJNoSQLTxRoutingPostProcessor(true)
                    .postProcessAfterInitialization(db, "db");
            EmbedJNoSQLTemplate template = new EmbedJNoSQLTemplate(proxied);
            EmbedJNoSQLTransactionManager tm = new EmbedJNoSQLTransactionManager(proxied);
            TransactionTemplate tt = new TransactionTemplate(tm);

            // outside any transaction: plain collections
            assertInstanceOf(DocumentCollection.class, template.documents("routing_tx"));
            assertFalse(template.documents("routing_tx") instanceof TransactionAwareDocumentCollection,
                    "without a transaction the template must hand out plain collections");

            tt.executeWithoutResult(s ->
                    assertInstanceOf(TransactionAwareDocumentCollection.class, template.documents("routing_tx"),
                            "the template resolves per call, so template-backed services route inside a tx"));
        } finally {
            db.close();
        }
    }

    @Test
    void unwrappingTheProxyYieldsTheRawDatabase() {
        EmbedJNoSQL db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .autoFlush(false)
                .buildConfig());
        try {
            EmbedJNoSQL proxied = (EmbedJNoSQL) new EmbedJNoSQLTxRoutingPostProcessor(true)
                    .postProcessAfterInitialization(db, "db");
            assertTrue(proxied != db, "post-processing must produce a proxy");
            assertEquals(db, EmbedJNoSQLTransactionManager.unwrapRoutingTarget(proxied),
                    "unwrap must yield the raw database bean");
            assertEquals(db, EmbedJNoSQLTransactionManager.unwrapRoutingTarget(db),
                    "an unproxied database unwraps to itself");
        } finally {
            db.close();
        }
    }
}
