package org.embeddedjnosql.db.spring.boot.parity;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTransactionManager;
import org.embeddedjnosql.db.spring.boot.parity.ParityApp.OrderTxService;
import org.embeddedjnosql.db.spring.boot.parity.ParityApp.ParityOrder;
import org.embeddedjnosql.db.spring.boot.parity.ParityApp.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The H2-parity suite: drop in the starter, annotate a class {@code @Entity}, and Spring gives
 * you an injectable repository, {@code @Transactional} semantics over the MVCC layer, and a
 * ready collection — no manual wiring, exactly like H2 + Spring Data JPA, but document-native.
 */
@SpringBootTest(properties = {"embedjnosql.storage-engine=IN_MEMORY"})
class EmbedJNoSQLHibernateParityTest {

    @Autowired
    EmbedJNoSQL db;

    @Autowired
    EmbedJNoSQLTransactionManager txManager;

    @Autowired
    EmbedRepository<ParityOrder, String> parityOrderRepository; // auto-registered by type!

    @Autowired
    OrderTxService orderTxService;

    private static String uid() {
        return "po-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void transactionManagerAndRepositoryBeansExist() {
        assertNotNull(txManager, "PlatformTransactionManager must be auto-configured");
        assertSame(db, txManager.getDatabase(), "the manager must target the auto-configured database");
        assertNotNull(parityOrderRepository, "an EmbedRepository bean must exist per @Entity, injectable by type");
    }

    @Test
    void entityCollectionMaterializedAtStartup() {
        assertTrue(db.getCollectionNames().contains("parity_orders"),
                "@Table collection must exist at startup (ddl-auto=update parity)");
    }

    @Test
    void repositoryCrudThroughAutoRegisteredBean() {
        String id = uid();
        ParityOrder saved = parityOrderRepository.save(new ParityOrder(id, "parity crud", Status.NEW));

        assertEquals("parity crud", parityOrderRepository.findById(id).orElseThrow().title);
        assertEquals(1, parityOrderRepository.findBy("order_title", "parity crud").size());
        assertEquals(1, parityOrderRepository.count());

        parityOrderRepository.deleteById(id);
        assertTrue(parityOrderRepository.findById(id).isEmpty());
        assertFalse(parityOrderRepository.existsById(id));
        assertNotNull(saved);
    }

    @Test
    void transactionalCommitPersists() {
        String id = uid();
        orderTxService.insertAndCommit(id, "committed");
        assertTrue(parityOrderRepository.findById(id).isPresent(), "committed write must persist");
        parityOrderRepository.deleteById(id);
    }

    @Test
    void transactionalRollbackDiscardsEverything() {
        String id = uid();
        long before = parityOrderRepository.count();

        var boom = assertThrows(IllegalStateException.class,
                () -> orderTxService.insertThenExplode(id, "doomed"));
        assertEquals("boom", boom.getMessage());

        assertTrue(parityOrderRepository.findById(id).isEmpty(), "rollback must discard the staged insert");
        assertEquals(before, parityOrderRepository.count(), "count must be unchanged after rollback");
    }

    @Test
    void requiresNewCommitsIndependentlyOfOuterRollback() {
        String doomed = uid();
        String survivor = uid();

        assertThrows(IllegalStateException.class,
                () -> orderTxService.outerRollsBackInnerCommits(doomed, survivor, "mixed"));

        assertTrue(parityOrderRepository.findById(doomed).isEmpty(), "outer rollback must discard");
        assertTrue(parityOrderRepository.findById(survivor).isPresent(), "REQUIRES_NEW must survive outer rollback");
        parityOrderRepository.deleteById(survivor);
    }

    @Test
    void updateInsideTransactionRollsBack() {
        String id = uid();
        parityOrderRepository.save(new ParityOrder(id, "v1", Status.NEW));

        var boom = assertThrows(IllegalStateException.class, () -> {
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.executeWithoutResult(s -> {
                var reloaded = parityOrderRepository.findById(id).orElseThrow();
                reloaded.title = "v2";
                parityOrderRepository.save(reloaded);
                throw new IllegalStateException("update boom");
            });
        });
        assertEquals("update boom", boom.getMessage());
        assertEquals("v1", parityOrderRepository.findById(id).orElseThrow().title,
                "a staged update must roll back with the transaction");
    }
}
