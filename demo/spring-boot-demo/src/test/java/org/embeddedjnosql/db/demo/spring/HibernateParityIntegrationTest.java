package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTransactionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The H2-parity integration suite at the demo level: a real {@code @SpringBootApplication}
 * where the entity ({@link HibernateOrder}) is just annotated with {@code @Entity} and
 * everything else — injectable typed repository, {@code @Transactional} semantics, a
 * materialized {@code hibernate_orders} collection — comes from the starter, exactly like
 * H2 + Spring Data JPA minus the relational engine.
 *
 * <p>Complements {@link HibernateIntegrationTest} (annotation mapping) by covering the
 * Spring experience around it: auto-registration, transaction commit/rollback, REQUIRES_NEW
 * suspend/resume, and staged-update rollback.</p>
 */
@SpringBootTest
@DisplayName("H2-parity in the demo app — auto-repository + @Transactional over Hibernate-annotated entities")
class HibernateParityIntegrationTest {

    @Autowired
    private EmbedJNoSQL db;

    @Autowired
    private EmbedJNoSQLTransactionManager txManager;

    @Autowired
    private EmbedRepository<HibernateOrder, String> orders; // auto-registered, injectable by type

    @Autowired
    private HibernateOrderTxService txService;

    private String uniqueRef(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private HibernateOrder newOrder(String reference) {
        var order = HibernateOrder.of(reference);
        order.setPriority(HibernateOrder.Priority.HIGH);
        return order;
    }

    @Test
    void autoRegisteredRepositoryIsInjectableByType() {
        assertSame(db, txManager.getDatabase(),
                "the tx manager must target the auto-configured (routed) database bean");
        assertEquals(HibernateOrder.class, orders.getEntityClass(),
                "the auto-registered repository must be typed to the entity");
    }

    @Test
    void entityCollectionMaterializedAtStartup() {
        assertTrue(db.getCollectionNames().contains("hibernate_orders"),
                "@Table collection must exist at startup (ddl-auto=update parity)");
    }

    @Test
    void repositoryCrudThroughAutoRegisteredBean() {
        String ref = uniqueRef("crud");
        HibernateOrder saved = orders.save(newOrder(ref));

        assertTrue(saved.getId() != null, "@UuidGenerator must populate the id through the repository");
        assertEquals(ref, orders.findById(saved.getId()).orElseThrow().getReference());
        assertFalse(orders.findBy("reference", ref).isEmpty());
        assertTrue(orders.existsById(saved.getId()));

        orders.deleteById(saved.getId());
        assertTrue(orders.findById(saved.getId()).isEmpty());
        assertFalse(orders.existsById(saved.getId()));
    }

    @Test
    void transactionalBatchCommitsAtomically() {
        String batchId = uniqueRef("batch");
        List<HibernateOrder> placed = txService.placeBatch(List.of(
                newOrder(batchId + "-a"), newOrder(batchId + "-b"), newOrder(batchId + "-c")));

        assertEquals(3, placed.size());
        placed.forEach(order ->
                assertTrue(orders.findById(order.getId()).isPresent(), "committed batch row must persist"));
        placed.forEach(order -> orders.deleteById(order.getId()));
    }

    @Test
    void transactionalRollbackDiscardsEverything() {
        String batchId = uniqueRef("doomed");
        int before = countByReferencePrefix(batchId);

        var boom = assertThrows(IllegalStateException.class,
                () -> txService.placeBatchThenExplode(List.of(
                        newOrder(batchId + "-a"), newOrder(batchId + "-b"))));
        assertEquals("payment failed", boom.getMessage());

        assertEquals(before, countByReferencePrefix(batchId),
                "rollback must discard every staged insert");
    }

    @Test
    void requiresNewCommitsIndependentlyOfOuterRollback() {
        String outerId = uniqueRef("outer");
        String survivorId = uniqueRef("survivor");

        assertThrows(IllegalStateException.class,
                () -> txService.doomedOuterWithCommittedInner(
                        List.of(newOrder(outerId)), newOrder(survivorId)));

        assertTrue(orders.findAll().stream().noneMatch(o -> outerId.equals(o.getReference())),
                "outer rollback must discard the doomed batch");
        assertTrue(orders.findAll().stream().anyMatch(o -> survivorId.equals(o.getReference())),
                "REQUIRES_NEW must survive the outer rollback");
        orders.findAll().stream()
                .filter(o -> survivorId.equals(o.getReference()))
                .forEach(o -> orders.deleteById(o.getId()));
    }

    @Test
    void stagedUpdateRollsBackWithTransaction() {
        String ref = uniqueRef("rename");
        HibernateOrder saved = orders.save(newOrder(ref));

        assertThrows(IllegalStateException.class, () -> txService.renameThenExplode(saved.getId(), "renamed"));

        assertEquals(ref, orders.findById(saved.getId()).orElseThrow().getReference(),
                "a staged update must roll back with the transaction");
        orders.deleteById(saved.getId());
    }

    @Test
    void transactionTemplateCommitsThroughTheSameManager() {
        String ref = uniqueRef("tpl");
        var tt = new TransactionTemplate(txManager);
        tt.executeWithoutResult(status -> orders.save(newOrder(ref)));

        assertTrue(orders.findBy("reference", ref).size() == 1,
                "a TransactionTemplate commit must persist through the routed collections");
        orders.findBy("reference", ref).forEach(o -> orders.deleteById(o.getId()));
    }

    private int countByReferencePrefix(String prefix) {
        return (int) orders.findAll().stream()
                .filter(o -> o.getReference() != null && o.getReference().startsWith(prefix))
                .count();
    }
}
