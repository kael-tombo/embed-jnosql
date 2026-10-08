package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.spring.boot.data.SdApp.SdItem;
import org.embeddedjnosql.db.spring.boot.data.SdApp.SdItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spring Data JPA-style repository experience, pinned by test: an interface extending
 * {@code JpaRepository}, derived query methods, paging/sorting, query-by-example, {@code @Transactional}
 * staging/rollback over the routed database bean — with no JPA provider and no relational store.
 */
@SpringBootTest
class EmbedJpaSpringDataIntegrationTest {

    @Autowired
    SdItemRepository items;

    @Autowired
    SdApp.BareRepository bare;

    @Autowired
    TransactionTemplate txTemplate; // auto-configured from the starter's tx manager

    private SdItem item(String id, String title, double price, boolean active) {
        return new SdItem(id, title, price, Instant.parse("2026-01-01T00:00:00Z"), active);
    }

    @Test
    void repositoryBeansAreInjectableByType() {
        assertNotNull(items);
        assertNotNull(bare, "a bare JpaRepository interface must also produce a working bean");
    }

    @Test
    void crudRoundTripThroughJpaRepositoryInterface() {
        SdItem saved = bare.save(item("crud-1", "Crud Item", 10.0, true));
        assertEquals("crud-1", saved.getId());

        assertTrue(bare.findById("crud-1").isPresent());
        assertEquals(1, bare.findAllById(List.of("crud-1")).size());
        assertTrue(bare.existsById("crud-1"));
        assertEquals(1, bare.count());

        bare.deleteById("crud-1");
        assertFalse(bare.existsById("crud-1"));
    }

    @Test
    void derivedQueriesTranslateToDocumentQueries() {
        items.save(item("d-1", "Alpha Widget", 50.0, true));
        items.save(item("d-2", "alpha gadget", 20.0, false));
        items.save(item("d-3", "Beta Widget", 80.0, true));

        // IgnoreCase on string equality — only d-1 carries the title (any casing)
        assertEquals(1, items.findByTitleIgnoreCase("alpha widget").size());

        // numeric comparison + count subject (both scoped via titles unique to this test:
        // a prior test in this shared context may leave rows behind — q-1/q-2 from QBE run
        // earlier in the same JVM if the alphabetical order puts QBE before this test)
        List<SdItem> cheap = items.findByPriceLessThan(30.0).stream()
                .filter(x -> x.getTitle().startsWith("Alpha") || x.getTitle().startsWith("alpha"))
                .toList();
        assertEquals(1, cheap.size());
        assertEquals("d-2", cheap.get(0).getId());

        // boolean keyword
        assertTrue(items.findByActiveTrue().stream().allMatch(SdItem::isActive));

        // prefix matching
        assertEquals(1, items.findByTitleStartingWith("Beta").size());

        // count subject — scoped to this test's fixture rows (active=false rows this test
        // created exactly one of; other tests' leftovers must not shift it)
        assertEquals(1, items.findAll().stream()
                .filter(x -> x.getId().startsWith("d-") && !x.isActive()).count());

        // order-by derived in the method name
        List<SdItem> byPrice = items.findByOrderByPriceDesc();
        assertTrue(byPrice.get(0).getPrice() >= byPrice.get(byPrice.size() - 1).getPrice());

        // exists subject
        assertTrue(items.existsByTitle("Alpha Widget"));

        // limiting + sort in one name
        List<SdItem> top = items.findFirst2ByPriceGreaterThanOrderByPriceAsc(0.0);
        assertEquals(2, top.size());
        assertTrue(top.get(0).getPrice() <= top.get(1).getPrice());
    }

    @Test
    void pagingAndSortingThroughPagingAndSortingRepository() {
        for (int i = 1; i <= 5; i++) {
            items.save(item("p-" + i, "Paged " + i, i * 10.0, true));
        }
        Page<SdItem> page = items.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "price")));
        assertEquals(2, page.getContent().size());
        assertTrue(page.getTotalElements() >= 5);
        assertTrue(page.getContent().get(0).getPrice() >= page.getContent().get(1).getPrice());

        List<SdItem> sorted = items.findAll(Sort.by(Sort.Direction.ASC, "price"));
        assertTrue(sorted.get(0).getPrice() <= sorted.get(sorted.size() - 1).getPrice());
    }

    @Test
    void queryByExampleHonorsProbeAndMatcher() {
        items.save(item("q-1", "Example One", 11.0, true));
        items.save(item("q-2", "Example Two", 22.0, false));

        SdItem probe = item(null, "Example One", 0.0, true); // null id ignored by default matcher
        ExampleMatcher matcher = ExampleMatcher.matching().withIgnorePaths("id", "price");
        List<SdItem> found = items.findAll(Example.of(probe, matcher));
        assertEquals(1, found.size());
        assertEquals("q-1", found.get(0).getId());

        // contains matcher + case-insensitivity
        ExampleMatcher contains = ExampleMatcher.matching()
                .withIgnorePaths("id", "price", "active", "createdAt")
                .withMatcher("title", ExampleMatcher.GenericPropertyMatcher::contains)
                .withIgnoreCase("title");
        SdItem probe2 = item(null, "example", 0.0, true);
        assertEquals(2, items.findAll(Example.of(probe2, contains)).size());

        // count + exists via example
        assertEquals(1, items.count(Example.of(probe, matcher)));
        assertTrue(items.exists(Example.of(probe, matcher)));
    }

    @Test
    void transactionalStagingAndRollbackThroughRepository() {
        String doomed = "tx-doomed";
        String committed = "tx-committed";

        // inside an active transaction the write stages and rolls back with it
        try {
            txTemplate.executeWithoutResult(status -> {
                items.save(item(doomed, "Doomed", 1.0, true));
                // read-your-own-writes inside the same transaction
                assertTrue(items.findById(doomed).isPresent());
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getMessage());
        }
        assertFalse(items.existsById(doomed), "rollback must discard the staged insert");

        txTemplate.executeWithoutResult(status -> items.save(item(committed, "Committed", 2.0, true)));
        assertTrue(items.existsById(committed), "a committed transaction must persist");
        items.deleteById(committed);
    }

    @Test
    void deleteDerivedAndBatchMethodsWork() {
        items.save(item("del-1", "Delete Me A", 5.0, true));
        items.save(item("del-2", "Delete Me B", 6.0, true));

        List<SdItem> hits = items.findByTitleStartingWith("Delete Me");
        assertEquals(2, hits.size());
        items.deleteAllInBatch(hits);
        assertEquals(0, items.findByTitleStartingWith("Delete Me").size());
    }
}
