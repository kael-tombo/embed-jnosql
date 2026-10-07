package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The H2-parity service: plain {@code @Transactional} over the auto-registered
 * {@link EmbedRepository} for the {@code hibernate_orders} entity — no manual transaction
 * code, no explicit collection resolution, exactly like a Spring Data JPA service over an
 * embedded H2.
 *
 * <p>The repository itself is auto-registered by the starter (bean name
 * {@code hibernateOrderRepository}); this service only declares what it needs. The
 * {@code REQUIRES_NEW} helper is invoked through a proxied self-reference because Spring AOP
 * transaction semantics — like for JPA — only apply through the proxy.</p>
 */
@Service
class HibernateOrderTxService {

    private final EmbedRepository<HibernateOrder, String> orders;
    private final ObjectProvider<HibernateOrderTxService> self;

    HibernateOrderTxService(EmbedRepository<HibernateOrder, String> orders,
                            ObjectProvider<HibernateOrderTxService> self) {
        this.orders = orders;
        this.self = self;
    }

    /** All-or-nothing batch: every insert commits together, or the exception discards all. */
    @Transactional
    public List<HibernateOrder> placeBatch(List<HibernateOrder> batch) {
        List<HibernateOrder> placed = new java.util.ArrayList<>(batch.size());
        orders.saveAll(batch).forEach(placed::add);
        return placed;
    }

    @Transactional
    public void placeBatchThenExplode(List<HibernateOrder> batch) {
        orders.saveAll(batch);
        if (orders.findAll().size() < batch.size()) {
            throw new IllegalStateException("read-your-own-writes failed inside the transaction");
        }
        throw new IllegalStateException("payment failed");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void insertInIndependentTx(HibernateOrder order) {
        orders.save(order);
    }

    /** Outer transaction rolls back; the REQUIRES_NEW batch commits independently. */
    @Transactional
    public void doomedOuterWithCommittedInner(List<HibernateOrder> doomedBatch, HibernateOrder survivor) {
        orders.saveAll(doomedBatch);
        self.getObject().insertInIndependentTx(survivor);
        throw new IllegalStateException("outer payment failed");
    }

    /** A staged update that must disappear when the transaction rolls back. */
    @Transactional
    public void renameThenExplode(String id, String newReference) {
        HibernateOrder reloaded = orders.findById(id).orElseThrow();
        reloaded.setReference(newReference);
        orders.save(reloaded);
        throw new IllegalStateException("rename failed");
    }
}
