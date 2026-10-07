package org.junify.db.demo.spring;

import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;
import org.junify.db.spring.boot.JunifyDBTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Second integration service: this one does NOT hand-maintain its own mapping — it relies on the
 * core's annotation mapper ({@link org.junify.db.adapter.jnosql.EntityMapper} via
 * {@link org.junify.db.adapter.jnosql.EclipseDocumentTemplate}) to translate
 * Hibernate/JPA-annotated entities to documents and back, exactly as a consumer wiring the
 * Jakarta NoSQL-style template into Spring would.
 */
@Service
class HibernateOrderService {

    private static final String ORDERS = "hibernate_orders";
    private static final String AUDIT = "hibernate_audit";

    private final DocumentCollection orders;
    private final DocumentCollection audit;

    HibernateOrderService(JunifyDBTemplate template) {
        this.orders = template.documents(ORDERS);
        this.audit = template.documents(AUDIT);
    }

    // ------------------------------------------------------------------
    // Orders — via the annotation-driven template
    // ------------------------------------------------------------------

    /** Inserts through the template; @PrePersist, @UuidGenerator, timestamps and @Formula all fire. */
    HibernateOrder insert(HibernateOrder order) {
        return template().insert(order);
    }

    /** Adds through the annotation mapper, then detaches a live instance from the raw document. */
    HibernateOrder insertAndDetach(String reference) {
        var stored = template().insert(HibernateOrder.of(reference));
        return HibernateOrder.fromSnapshot(findDocument(stored.getId()));
    }

    Optional<HibernateOrder> find(String id) {
        return template().find(HibernateOrder.class, id);
    }

    HibernateOrder update(HibernateOrder order) {
        return template().update(order);
    }

    boolean deleteById(String id) {
        template().deleteById(HibernateOrder.class, id);
        return template().find(HibernateOrder.class, id).isEmpty();
    }

    List<HibernateOrder> findAll() {
        return template().findAll(HibernateOrder.class);
    }

    List<HibernateOrder> findByReference(String reference) {
        return template().find(HibernateOrder.class, Query.eq("reference", reference));
    }

    List<HibernateOrder> findByPriority(HibernateOrder.Priority priority) {
        return template().find(HibernateOrder.class, Query.eq("priority", priority.name()));
    }

    /** Ordered, windowed NoSQL query over the annotation-mapped collection. */
    List<HibernateOrder> pageExpensive(int offset, int limit) {
        return template().find(HibernateOrder.class,
                Query.gt("totalCost", 0).sortByDesc("basePrice").limit(limit).offset(offset));
    }

    long countOrders() {
        return template().count(HibernateOrder.class);
    }

    /** Batch insert through the template (annotation mapping for every element). */
    List<HibernateOrder> insertAll(List<HibernateOrder> batch) {
        template().insertAll(batch);
        return batch;
    }

    /** Exists check through the template. */
    boolean existsById(String id) {
        return template().existsById(HibernateOrder.class, id);
    }

    // ------------------------------------------------------------------
    // Raw-storage round trip: detach a snapshot of an inserted entity and
    // re-materialize it live, with the mapper deriving column keys itself.
    // ------------------------------------------------------------------

    Map<String, Object> rawColumnsOf(String id) {
        Document doc = orders.findById(id);
        return doc == null ? Map.of() : doc.fields();
    }

    private DocumentCollection collection() { return orders; }

    private org.junify.db.adapter.jnosql.EclipseDocumentTemplate template() {
        return org.junify.db.adapter.jnosql.EclipseDocumentTemplate.of(orders);
    }

    private Document findDocument(String id) {
        return orders.findById(id);
    }

    /** Feed a detached snapshot into a live instance WITHOUT the template (pure EntityMapper path). */
    static HibernateOrder remap(Document doc) {
        return org.junify.db.adapter.jnosql.EntityMapper.fromDocument(doc, HibernateOrder.class);
    }

    /** Map a live entity to a document without persisting (pure EntityMapper path). */
    static Document remapToDocument(HibernateOrder order) {
        return org.junify.db.adapter.jnosql.EntityMapper.toDocument(order);
    }

    // ------------------------------------------------------------------
    // Audit records — @Immutable + @NaturalId
    // ------------------------------------------------------------------

    HibernateAuditRecord insertAudit(HibernateAuditRecord record) {
        return auditTemplate().insert(record);
    }

    Optional<HibernateAuditRecord> findAudit(String id) {
        return auditTemplate().find(HibernateAuditRecord.class, id);
    }

    HibernateAuditRecord updateAudit(HibernateAuditRecord record) {
        return auditTemplate().update(record);
    }

    boolean isNaturalIdTicket() {
        try {
            var field = HibernateAuditRecord.class.getDeclaredField("ticketNumber");
            return org.junify.db.adapter.jnosql.EntityMapper.isNaturalId(field);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    String auditCollectionName() {
        return org.junify.db.adapter.jnosql.EntityMapper.getCollectionName(HibernateAuditRecord.class);
    }

    private org.junify.db.adapter.jnosql.EclipseDocumentTemplate auditTemplate() {
        return org.junify.db.adapter.jnosql.EclipseDocumentTemplate.of(audit);
    }

    /** Count documents in the raw audit collection, independent of the template. */
    long auditCount() {
        return audit.count();
    }

    /** Group order ids by priority straight from storage (aggregation over stored enum names). */
    Map<String, List<String>> idsByPriority() {
        return findAllDocuments().stream()
                .collect(Collectors.groupingBy(
                        d -> String.valueOf((Object) d.getRaw("priority")),
                        Collectors.mapping(Document::getId, Collectors.toList())));
    }

    private List<Document> findAllDocuments() {
        return orders.findAll();
    }

    /** Utility exposing the collection for direct asserts (raw column names, ttl, etc.). */
    DocumentCollection ordersCollection() {
        return collection();
    }

    Function<String, Optional<HibernateOrder>> finder() {
        return this::find;
    }
}
