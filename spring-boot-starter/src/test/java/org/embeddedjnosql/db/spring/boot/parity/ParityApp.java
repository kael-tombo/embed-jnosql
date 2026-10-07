package org.embeddedjnosql.db.spring.boot.parity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

/**
 * A minimal H2-style application: just {@code @SpringBootApplication} + the starter. Everything
 * else — the database bean, the transaction manager, {@code parityOrderRepository}, the
 * materialized {@code parity_orders} collection — comes from auto-configuration alone.
 *
 * <p>Lives in its own sub-package on purpose: an application component-scans its own package, and
 * this package contains no embed-jnosql classes, mirroring a real {@code com.example.app} layout.</p>
 */
@SpringBootApplication
public class ParityApp {

    enum Status {NEW, SHIPPED}

    @Entity
    @Table(name = "parity_orders")
    static class ParityOrder {
        @Id
        String id;
        @Column(name = "order_title")
        String title;
        @Enumerated(EnumType.STRING)
        Status status;

        ParityOrder() {
        }

        ParityOrder(String id, String title, Status status) {
            this.id = id;
            this.title = title;
            this.status = status;
        }
    }

    /** A plain Spring service: no embed-jnosql API except the injected repository/template. */
    static class OrderTxService {
        private final EmbedRepository<ParityOrder, String> repo;
        private final EmbedJNoSQLTemplate template;
        /**
         * Self-reference to the PROXIED bean: {@code @Transactional(REQUIRES_NEW)} only applies
         * through the AOP proxy, so a REQUIRES_NEW method must not be called via {@code this}
         * (the same universal Spring AOP rule as Spring Data JPA — not starter-specific).
         */
        private final ObjectProvider<OrderTxService> self;

        OrderTxService(EmbedRepository<ParityOrder, String> repo, EmbedJNoSQLTemplate template,
                       ObjectProvider<OrderTxService> self) {
            this.repo = repo;
            this.template = template;
            this.self = self;
        }

        @Transactional
        public ParityOrder insertAndCommit(String id, String title) {
            return repo.save(new ParityOrder(id, title, Status.NEW));
        }

        @Transactional
        public int countInsideTx() {
            return repo.findAll().size();
        }

        @Transactional
        public void insertThenExplode(String id, String title) {
            repo.save(new ParityOrder(id, title, Status.NEW));
            // read-your-own-writes: the staged insert is visible inside the SAME transaction
            assertEquals(1, template.documents("parity_orders").findAll().size(),
                    "query reads must see staged writes");
            throw new IllegalStateException("boom");
        }

        @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        public void insertInIndependentTx(String id, String title) {
            repo.save(new ParityOrder(id, title, Status.NEW));
        }

        @Transactional
        public void outerRollsBackInnerCommits(String doomedId, String survivorId, String title) {
            repo.save(new ParityOrder(doomedId, title, Status.NEW));
            self.getObject().insertInIndependentTx(survivorId, title);
            throw new IllegalStateException("outer boom");
        }

        private static void assertEquals(int expected, int actual, String message) {
            if (expected != actual) {
                throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
            }
        }
    }

    @Bean
    OrderTxService orderTxService(EmbedRepository<ParityOrder, String> repo, EmbedJNoSQLTemplate template,
                                  ObjectProvider<OrderTxService> self) {
        return new OrderTxService(repo, template, self);
    }
}
