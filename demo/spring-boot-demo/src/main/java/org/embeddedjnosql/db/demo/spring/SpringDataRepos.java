package org.embeddedjnosql.db.demo.spring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository interfaces over the demo entities — written exactly like
 * {@code JpaRepository}s for a relational store, backed by the embed-jnosql document engine:
 * derived query methods (PartTree keywords over mapped columns, including the
 * {@code @Column(name="order_email")} alias), paging/sorting, query-by-example.
 */
public interface SpringDataRepos {

    /** Derived queries over {@link HibernateOrder} (collection {@code hibernate_orders}). */
    interface OrderJpaRepository extends JpaRepository<HibernateOrder, String> {

        List<HibernateOrder> findByReferenceStartingWith(String prefix);

        List<HibernateOrder> findByEmailIgnoreCase(String email);

        List<HibernateOrder> findByBasePriceLessThanOrderByBasePriceAsc(double price);

        long countByPriority(HibernateOrder.Priority priority);

        List<HibernateOrder> findByPriority(HibernateOrder.Priority priority);

        boolean existsByReference(String reference);
    }
}
