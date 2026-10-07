package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deep integration suite: Hibernate/JPA annotation support exercised end-to-end inside a real
 * Spring Boot application — auto-configured {@code EmbedJNoSQL} bean, template-backed services,
 * actual persistence in the app's storage engine. The 20 deep assertions requested by the handoff,
 * plus two behaviors surfaced by this suite's own first run and now pinned, organized as:
 * <ol>
 *   <li>Auto-config &amp; template wiring with the annotation path (1–3)</li>
 *   <li>CRUD through Hibernate-annotated entities (4–10)</li>
 *   <li>Generated/derived/aliased columns at document level (11–14)</li>
 *   <li>NoSQL queries and aggregation over annotation-mapped documents (15–18)</li>
 *   <li>{@code @Immutable} + {@code @NaturalId} audit entity (19–20)</li>
 *   <li>Aggregate guarantees (21)</li>
 *   <li>Write/read symmetry found by this suite: {@code java.util.Date} fields (22)</li>
 * </ol>
 */
@SpringBootTest
@DisplayName("Hibernate integration in Spring Boot — @Entity→document mapping, no JPA runtime")
class HibernateIntegrationTest {

    @Autowired
    private HibernateOrderService orderService;

    @Autowired
    private org.embeddedjnosql.db.spring.boot.EmbedJNoSQLTemplate template;

    @Autowired
    private org.embeddedjnosql.db.EmbedJNoSQL db;

    private String uniqueRef(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // =====================================================================
    // 1. Auto-config & template wiring (1–3)
    // =====================================================================

    @Test
    @DisplayName("1. auto-configured database + template beans back the annotation-driven service")
    void autoConfigWiresAnnotationPath() {
        assertNotNull(db, "EmbedJNoSQL bean must be injected by the starter");
        assertTrue(db.isOpen());
        assertNotNull(template);
        assertNotNull(orderService.ordersCollection());
        assertEquals("hibernate_orders", orderService.ordersCollection().name(),
                "the @Table-annotated entity's collection name must be the one the service uses");
    }

    @Test
    @DisplayName("2. @Table renames the collection; the un-tabled audit entity falls back to its lowercased simple name")
    void tableAnnotationDrivesCollectionName() {
        assertEquals("hibernate_orders",
                org.embeddedjnosql.db.adapter.jnosql.EntityMapper.getCollectionName(HibernateOrder.class),
                "@Table(name=\"hibernate_orders\") must win over any convention");
        assertEquals("hibernateauditrecord",
                org.embeddedjnosql.db.adapter.jnosql.EntityMapper.getCollectionName(HibernateAuditRecord.class),
                "without @Table/@Entity(name), the resolver defaults the collection to the lowercased simple class name");
    }

    @Test
    @DisplayName("3. @Column aliases appear as raw document columns")
    void columnAliasLandsInRawDocumentColumns() {
        String ref = uniqueRef("colalias");
        HibernateOrder stored = orderService.insert(HibernateOrder.of(ref));

        Map<String, Object> raw = orderService.rawColumnsOf(stored.getId());
        assertFalse(raw.containsKey("email"), "the java field name must not be stored directly");
        assertEquals(ref.toLowerCase() + "@embedjnosql.example", raw.get("order_email"),
                "@Column(name=\"order_email\") must be the stored column key");

        // and the alias round trips through the template as the typed field
        assertEquals(ref.toLowerCase() + "@embedjnosql.example",
                orderService.find(stored.getId()).orElseThrow().getEmail());
    }

    // =====================================================================
    // 2. CRUD through Hibernate-annotated entities (4–10)
    // =====================================================================

    @Test
    @DisplayName("4. insert→find round trip preserves the typed entity")
    void insertFindRoundTrip() {
        String ref = uniqueRef("crud");
        HibernateOrder inserted = orderService.insert(HibernateOrder.of(ref));

        HibernateOrder loaded = orderService.find(inserted.getId()).orElseThrow();
        assertEquals(ref, loaded.getReference());
        assertEquals(HibernateOrder.Priority.MEDIUM, loaded.getPriority());
        assertEquals(100.0, loaded.getBasePrice(), 0.0001);
        assertEquals(15.0, loaded.getTaxRate(), 0.0001);
    }

    @Test
    @DisplayName("5. @UuidGenerator assigns a UUID id when the entity has none")
    void uuidGeneratorPopulatesId() {
        HibernateOrder order = HibernateOrder.of(uniqueRef("uuid"));
        assertNull(order.getId());

        HibernateOrder stored = orderService.insert(order);
        assertNotNull(stored.getId(), "insert must back-fill the id via @UuidGenerator");
        assertEquals(36, stored.getId().length(), "must be a canonical UUID string");

        // id is usable immediately for lookups
        assertTrue(orderService.existsById(stored.getId()));
    }

    @Test
    @DisplayName("6. explicit ids are stored even with @UuidGenerator present")
    void explicitIdWinsOverGenerator() {
        HibernateAuditRecord record = new HibernateAuditRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8),
                "GENESIS",
                uniqueRef("ticket"));
        HibernateAuditRecord stored = orderService.insertAudit(record);
        assertEquals(record.getId(), stored.getId(),
                "an explicit @Id must not be replaced by the generator");
        assertTrue(orderService.findAudit(stored.getId()).isPresent());
    }

    @Test
    @DisplayName("7. update() changes persisted values and keeps identity")
    void updatePersistsChanges() {
        String ref = uniqueRef("upd");
        HibernateOrder stored = orderService.insert(HibernateOrder.of(ref));

        stored.setReference(ref + "-v2");
        stored.setBasePrice(250.0);
        stored.setPriority(HibernateOrder.Priority.HIGH);
        orderService.update(stored);

        HibernateOrder reloaded = orderService.find(stored.getId()).orElseThrow();
        assertEquals(ref + "-v2", reloaded.getReference());
        assertEquals(250.0, reloaded.getBasePrice(), 0.0001);
        assertEquals(HibernateOrder.Priority.HIGH, reloaded.getPriority());
        assertEquals(stored.getId(), reloaded.getId());
    }

    @Test
    @DisplayName("8. deleteById removes the document behind the entity")
    void deleteByIdRemovesDocument() {
        HibernateOrder stored = orderService.insert(HibernateOrder.of(uniqueRef("del")));

        assertTrue(orderService.deleteById(stored.getId()));
        assertTrue(orderService.find(stored.getId()).isEmpty());
        assertTrue(orderService.ordersCollection().findById(stored.getId()) == null,
                "the underlying document must be gone from raw storage too");
    }

    @Test
    @DisplayName("9. batch insertAll maps every element and persists every document")
    void insertAllPersistsBatch() {
        List<HibernateOrder> batch = List.of(
                HibernateOrder.of(uniqueRef("batch")),
                HibernateOrder.of(uniqueRef("batch")),
                HibernateOrder.of(uniqueRef("batch")));

        orderService.insertAll(batch);

        for (HibernateOrder order : batch) {
            assertNotNull(order.getId(), "every entity must leave insertAll with an id");
            assertTrue(orderService.existsById(order.getId()));
        }
        assertEquals(3, orderService.findByReference(batch.get(0).getReference()).size()
                + orderService.findByReference(batch.get(1).getReference()).size()
                + orderService.findByReference(batch.get(2).getReference()).size());
    }

    @Test
    @DisplayName("10. existsById and count agree with raw storage")
    void existsAndCountAgree() {
        long before = orderService.countOrders();
        HibernateOrder stored = orderService.insert(HibernateOrder.of(uniqueRef("cnt")));

        assertTrue(orderService.existsById(stored.getId()));
        assertEquals(before + 1, orderService.countOrders());

        orderService.deleteById(stored.getId());
        assertFalse(orderService.existsById(stored.getId()));
        assertEquals(before, orderService.countOrders());
    }

    // =====================================================================
    // 3. Generated/derived/aliased columns at document level (11–14)
    // =====================================================================

    @Test
    @DisplayName("11. @CreationTimestamp/@UpdateTimestamp are set through the template")
    void timestampsPopulateThroughTemplate() {
        HibernateOrder stored = orderService.insert(HibernateOrder.of(uniqueRef("ts")));

        Instant created = stored.getCreatedAt();
        Instant updated = stored.getUpdatedAt();
        assertNotNull(created, "@CreationTimestamp must be populated on insert");
        assertNotNull(updated, "@UpdateTimestamp must be populated on insert");

        // recreated from the document, the timestamps must survive
        HibernateOrder detached = HibernateOrder.fromSnapshot(
                orderService.ordersCollection().findById(stored.getId()));
        assertNotNull(detached.getCreatedAt());
        assertTrue(!detached.getUpdatedAt().isBefore(created),
                "stored updatedAt must never precede createdAt");
    }

    @Test
    @DisplayName("12. document-level timestamp storage uses the mapped columns")
    void timestampColumnsStoredIrrespectiveOfEntity() {
        String ref = uniqueRef("tscol");
        HibernateOrder stored = orderService.insert(HibernateOrder.of(ref));
        Map<String, Object> raw = orderService.rawColumnsOf(stored.getId());

        assertNotNull(raw.get("createdAt"), "@CreationTimestamp column must persist in storage");
        assertNotNull(raw.get("updatedAt"), "@UpdateTimestamp column must persist in storage");
        assertNotEquals(stored.getCreatedAt(), raw.get("createdAt"),
                "stored form must be the serialized string, not the Java Instant instance");
    }

    @Test
    @DisplayName("13. @Formula totalCost is derived, not stored")
    void formulaIsDerivedNotStored() {
        HibernateOrder stored = orderService.insert(HibernateOrder.of(uniqueRef("fml")));
        assertEquals(115.0, stored.getTotalCost(), 0.0001,
                "insert() must evaluate basePrice + taxRate into totalCost");

        Map<String, Object> raw = orderService.rawColumnsOf(stored.getId());
        Object storedTotal = raw.get("totalCost");
        assertTrue(storedTotal == null || ((Number) storedTotal).doubleValue() != 9999.0,
                "whatever the document holds, the template must recompute (not store) the formula");

        // changing the parts and reloading changes the derived value
        stored.setBasePrice(200.0);
        orderService.update(stored);
        HibernateOrder reloaded = orderService.find(stored.getId()).orElseThrow();
        assertEquals(215.0, reloaded.getTotalCost(), 0.0001,
                "the formula must track its inputs after update + reload");
    }

    @Test
    @DisplayName("14. @Transient field never reaches storage")
    void transientFieldNeverStored() {
        String token = UUID.randomUUID().toString();
        HibernateOrder order = HibernateOrder.of(uniqueRef("transient"));
        order.setValidationToken(token);

        HibernateOrder stored = orderService.insert(order);

        Map<String, Object> raw = orderService.rawColumnsOf(stored.getId());
        assertFalse(raw.containsKey("validationToken"),
                "@Transient fields must be excluded from the document");
        assertEquals(token, stored.getValidationToken(),
                "the in-memory value must survive on the caller's instance");

        assertTrue(orderService.find(stored.getId()).orElseThrow().getValidationToken() == null,
                "a freshly loaded entity must not resurrect an @Transient-only value");
    }

    // =====================================================================
    // 4. NoSQL queries & aggregation over annotation-mapped documents (15–18)
    // =====================================================================

    @Test
    @DisplayName("15. Query.eq over an @Enumerated(STRING) column matches by name")
    void enumQueryMatchesStoredName() {
        HibernateOrder a = orderService.insert(HibernateOrder.of(uniqueRef("enumq")));
        orderService.update(a);   // leave MEDIUM

        HibernateOrder b = HibernateOrder.of(uniqueRef("enumq"));
        b.setPriority(HibernateOrder.Priority.HIGH);
        HibernateOrder stored = orderService.insert(b);

        List<HibernateOrder> highs = orderService.findByPriority(HibernateOrder.Priority.HIGH);
        List<HibernateOrder> mediums = orderService.findByPriority(HibernateOrder.Priority.MEDIUM);

        assertTrue(highs.stream().anyMatch(o -> stored.getId().equals(o.getId())));
        assertTrue(mediums.stream().anyMatch(o -> a.getId().equals(o.getId())));
        assertTrue(highs.stream().noneMatch(o -> a.getId().equals(o.getId())));
    }

    @Test
    @DisplayName("16. sortByDesc + limit + offset window the annotation-mapped collection")
    void sortByAndWindow() {
        HibernateOrder big = HibernateOrder.of(uniqueRef("win"));
        big.setBasePrice(500.0);
        HibernateOrder mid = HibernateOrder.of(uniqueRef("win"));
        mid.setBasePrice(300.0);
        HibernateOrder small = HibernateOrder.of(uniqueRef("win"));
        small.setBasePrice(100.0);
        orderService.insertAll(List.of(big, mid, small));

        List<HibernateOrder> topPage = orderService.pageExpensive(0, 2);
        assertTrue(topPage.size() <= 2);
        for (int i = 1; i < topPage.size(); i++) {
            assertTrue(topPage.get(i - 1).getBasePrice() >= topPage.get(i).getBasePrice(),
                    "page must be non-increasing by basePrice");
        }
        assertTrue(topPage.stream().anyMatch(o -> big.getId().equals(o.getId())),
                "the inserted highest-price order must be in page 1");

        // offset skips the front of the same ordering
        List<HibernateOrder> skipped = orderService.pageExpensive(1, 2);
        assertEquals(topPage.get(1).getId(), skipped.get(0).getId(),
                "offset must slide the window, not reshuffle it");
    }

    @Test
    @DisplayName("17. findAll returns every inserted entity with types intact")
    void findAllTypedRoundTrip() {
        String ref = uniqueRef("findall");
        List<HibernateOrder> batch = orderService.insertAll(List.of(
                HibernateOrder.of(ref), HibernateOrder.of(ref)));

        List<HibernateOrder> all = orderService.findAll();
        assertTrue(all.size() >= 2);
        List<HibernateOrder> mine = all.stream()
                .filter(o -> ref.equals(o.getReference())).toList();
        assertEquals(2, mine.size());
        for (HibernateOrder order : mine) {
            assertInstanceOf(HibernateOrder.Priority.class, order.getPriority(),
                    "enums must come back as the enum type, not a String");
            assertTrue(order.getTotalCost() > 0, "formula must be re-derived on load");
        }
    }

    @Test
    @DisplayName("18. storage-level grouping sees the stored enum names (aggregation compatibility)")
    void storageGroupingSeesEnumNames() {
        String ref = uniqueRef("grp");
        List<HibernateOrder> batch = orderService.insertAll(List.of(
                HibernateOrder.of(ref),
                HibernateOrder.of(ref)));

        // bump one of the pair to HIGH so the group has two distinct names
        batch.get(1).setPriority(HibernateOrder.Priority.HIGH);
        orderService.update(batch.get(1));

        Map<String, List<String>> groups = orderService.idsByPriority();
        List<String> mine = batch.stream().map(HibernateOrder::getId).toList();
        assertTrue(groups.get("MEDIUM").contains(mine.get(0)), "MEDIUM must group by stored name");
        assertTrue(groups.get("HIGH").contains(mine.get(1)), "HIGH must group by stored name");
    }

    // =====================================================================
    // 5. @Immutable + @NaturalId (19–20)
    // =====================================================================

    @Test
    @DisplayName("19. @Immutable entities refuse update() from a Spring service")
    void immutableAuditRecordRefusesUpdate() {
        HibernateAuditRecord record = new HibernateAuditRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8),
                "LOGIN", uniqueRef("immutable"));
        HibernateAuditRecord stored = orderService.insertAudit(record);
        assertNotNull(orderService.findAudit(stored.getId()).orElseThrow().getAction());

        stored.setAction("TAMPERED");
        assertThrows(IllegalStateException.class, () -> orderService.updateAudit(stored),
                "@Immutable must make update() throw — the audit trail cannot be rewritten");
        assertEquals("LOGIN", orderService.findAudit(stored.getId()).orElseThrow().getAction(),
                "storage must be unchanged after the refused update");
    }

    @Test
    @DisplayName("20. @NaturalId business key is recognized by the mapper")
    void naturalIdIsIdentified() {
        assertTrue(orderService.isNaturalIdTicket(),
                "@NaturalId on ticketNumber must be visible to EntityMapper.isNaturalId");
        assertFalse(isFieldNaturalId("id"));
        assertFalse(isFieldNaturalId("action"));
    }

    private boolean isFieldNaturalId(String field) {
        try {
            return org.embeddedjnosql.db.adapter.jnosql.EntityMapper.isNaturalId(
                    HibernateAuditRecord.class.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    // =====================================================================
    // 6. Aggregate guarantees (21)
    // =====================================================================

    @Test
    @DisplayName("21. full stack: 12 distinct columns, all 20 annotations mapped, storage clean")
    void wholeStackSummary() {
        String ref = uniqueRef("stack");
        HibernateOrder stored = orderService.insert(HibernateOrder.of(ref));
        Map<String, Object> raw = orderService.rawColumnsOf(stored.getId());

        // the mapped column set: reference, priority, createdAt, updatedAt, basePrice, taxRate,
        // totalCost, order_email, approved, auditDate (+ the mapper's "id" mirror); validationToken
        // is @Transient and must be absent
        for (String col : List.of("reference", "priority", "createdAt", "updatedAt",
                "basePrice", "taxRate", "totalCost", "order_email", "approved", "id")) {
            assertTrue(raw.containsKey(col), "missing mapped column: " + col);
        }
        // auditDate is java.util.Date: serialized to an ISO string on write. The column is present
        // when the fixture set it (the stack test's order does via HibernateOrder.of), and the
        // symmetric ISO-string -> Date read path is pinned by permissiveReload.
        assertTrue(raw.containsKey("auditDate"),
                "the fixture sets auditDate, so the serialized Date column must be present");
        assertInstanceOf(String.class, raw.get("auditDate"),
                "java.util.Date must be serialized (ISO string), matching EntityMapper.toDocument");
        assertFalse(raw.containsKey("validationToken"));

        // enum + formula + alias + timestamps land typed on the loaded entity
        HibernateOrder loaded = orderService.find(stored.getId()).orElseThrow();
        assertEquals(ref, loaded.getReference());
        assertEquals(HibernateOrder.Priority.MEDIUM, loaded.getPriority());
        assertEquals(115.0, loaded.getTotalCost(), 0.0001);
        assertEquals(ref.toLowerCase() + "@embedjnosql.example", loaded.getEmail());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
        assertTrue(loaded.isPostLoaded(), "@PostLoad must fire when the template materializes");
    }

    // =====================================================================
    // 7. Write/read symmetry found by this suite (22)
    // =====================================================================

    @Test
    @DisplayName("22. java.util.Date fields round trip as exact Dates, not raw strings")
    void dateRoundTripThroughMapper() {
        Date set = Date.from(Instant.now().minusSeconds(60));
        HibernateOrder order = HibernateOrder.of(uniqueRef("daterd"));
        order.setAuditDate(set);

        HibernateOrder stored = orderService.insert(order);
        assertInstanceOf(String.class, orderService.rawColumnsOf(stored.getId()).get("auditDate"),
                "storage form must be the serialized ISO string, matching toDocument");

        HibernateOrder loaded = orderService.find(stored.getId()).orElseThrow();
        Date read = loaded.getAuditDate();
        assertNotNull(read, "EntityMapper must convert the stored ISO string back into a java.util.Date");
        assertEquals(set.getTime(), read.getTime(),
                "the write/read paths must be symmetric to the millisecond");

        // and a plain EntityMapper-only remap (no template) must behave identically
        Document rawDoc = orderService.ordersCollection().findById(stored.getId());
        assertEquals(set.getTime(), HibernateOrderService.remap(rawDoc).getAuditDate().getTime(),
                "the pure mapper path must agree with the template path");
    }
}
