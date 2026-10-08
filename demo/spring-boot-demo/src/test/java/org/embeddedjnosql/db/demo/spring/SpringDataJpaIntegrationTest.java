package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.demo.spring.HibernateOrder.Priority;
import org.embeddedjnosql.db.demo.spring.SpringDataRepos.OrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spring Data JPA-style repository surface exercised over the demo entities: an interface
 * extending {@code JpaRepository} with derived query methods (PartTree over mapped columns,
 * including the {@code order_email} alias), paging/sorting, query-by-example, and
 * {@code @Transactional} staging through the document engine — with no JPA provider, no
 * relational store.
 */
@SpringBootTest
class SpringDataJpaIntegrationTest {

    @Autowired
    OrderJpaRepository orders;

    private static int seq = 0;

    private static String uniqueRef() {
        return "SD-" + System.nanoTime() + "-" + (seq++);
    }

    @Test
    void repositoryBeanIsInjectableByType() {
        assertNotNull(orders, "a demo @Entity JpaRepository interface must produce a working bean");
    }

    @Test
    void crudRoundTripThroughRepository() {
        HibernateOrder order = HibernateOrder.of(uniqueRef());
        HibernateOrder saved = orders.save(order);
        assertNotNull(saved.getId());

        assertTrue(orders.findById(saved.getId()).isPresent());
        assertEquals(1, orders.findAllById(List.of(saved.getId())).size());
        orders.deleteById(saved.getId());
        assertFalse(orders.findById(saved.getId()).isPresent());
    }

    @Test
    void derivedQueriesTranslateToDocumentQueries() {
        String bucket = uniqueRef();
        HibernateOrder a = orders.save(order(bucket, "A", 50.0, Priority.HIGH, true));
        orders.save(order(bucket, "B", 20.0, Priority.LOW, false));
        orders.save(order(bucket, "C", 80.0, Priority.HIGH, true));

        // simple + IgnoreCase over the @Column(order_email) alias
        assertEquals(1, orders.findByEmailIgnoreCase(a.getEmail()).size());

        // prefix over reference
        List<HibernateOrder> prefixed = orders.findByReferenceStartingWith(bucket + "-");
        assertEquals(3, prefixed.size());

        // numeric comparison with derived ordering
        List<HibernateOrder> cheap = orders.findByBasePriceLessThanOrderByBasePriceAsc(30.0)
                .stream().filter(x -> x.getReference().startsWith(bucket + "-")).toList();
        assertEquals(1, cheap.size());
        assertEquals("B", cheap.get(0).getReference().substring(bucket.length() + 1));

        // enum predicate over the @Enumerated(STRING) column, scoped to this test's bucket
        // (A and C are both HIGH)
        assertEquals(2, orders.findByPriority(Priority.HIGH).stream()
                .filter(x -> x.getReference().startsWith(bucket + "-")).count());
        assertEquals(1, orders.countByPriority(Priority.LOW)
                - nonBucketLow(bucket));
        assertTrue(orders.existsByReference(a.getReference()));

        cleanupBucket(bucket);
    }

    @Test
    void pagingAndSortingThroughTheInterface() {
        String bucket = uniqueRef();
        for (int i = 1; i <= 6; i++) {
            orders.save(order(bucket, "P" + i, i * 10.0, Priority.MEDIUM, true));
        }
        var page = orders.findAll(PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "basePrice")));
        assertEquals(3, page.getContent().size());
        assertTrue(page.getTotalElements() >= 6);

        List<HibernateOrder> sorted = orders.findAll(Sort.by(Sort.Direction.DESC, "basePrice"))
                .stream().filter(x -> x.getReference().startsWith(bucket + "-")).toList();
        assertEquals(6, sorted.size());
        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue(sorted.get(i).getBasePrice() >= sorted.get(i + 1).getBasePrice());
        }
        cleanupBucket(bucket);
    }

    @Test
    void queryByExampleWithMatcher() {
        String bucket = uniqueRef();
        HibernateOrder a = orders.save(order(bucket, "Alpha", 11.0, Priority.MEDIUM, true));
        orders.save(order(bucket, "Beta", 22.0, Priority.LOW, false));

        // The probe carries only `reference`; every primitive/derived field of HibernateOrder
        // (basePrice, taxRate, approved, prePersisted, postLoaded, totalCost) is non-null by
        // default, so an all-matching QBE predicate must explicitly ignore them — exactly the
        // pitfall a JPA developer hits with primitive columns.
        HibernateOrder probe = new HibernateOrder();
        probe.setReference(bucket + "-Alpha");
        ExampleMatcher matcher = ExampleMatcher.matching().withIgnorePaths(
                "id", "createdAt", "updatedAt",
                "basePrice", "taxRate", "totalCost",
                "approved", "prePersisted", "postLoaded");
        List<HibernateOrder> found = orders.findAll(Example.of(probe, matcher));
        List<HibernateOrder> buck = found.stream()
                .filter(x -> x.getReference().startsWith(bucket + "-")).toList();
        assertEquals(1, buck.size());
        assertEquals(a.getId(), found.get(0).getId());
        cleanupBucket(bucket);
    }

    // ---------------------------------------------------------------------

    private long nonBucketLow(String bucket) {
        // LOW rows from prior runs/tests are outside this probe's bucket scope
        return orders.findByPriority(Priority.LOW).stream()
                .filter(x -> !x.getReference().startsWith(bucket + "-")).count();
    }

    private HibernateOrder order(String bucket, String tag, double price, Priority priority, boolean approved) {
        HibernateOrder order = HibernateOrder.of(bucket + "-" + tag);
        order.setBasePrice(price);
        order.setTaxRate(0.0);
        order.setPriority(priority);
        order.setApproved(approved);
        return order;
    }

    private void cleanupBucket(String bucket) {
        orders.findAll().stream()
                .filter(x -> x.getReference().startsWith(bucket + "-"))
                .forEach(x -> orders.deleteById(x.getId()));
    }
}
