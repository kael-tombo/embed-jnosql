package org.embeddedjnosql.db.demo.annotation.service;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.embeddedjnosql.db.demo.annotation.model.CustomerAccount;
import org.embeddedjnosql.db.demo.annotation.model.InvoiceRecord;
import org.embeddedjnosql.db.demo.annotation.model.InvoiceStatus;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Demonstrates annotation-driven document persistence.
 *
 * <p>JPA and Hibernate annotations on the entity classes are resolved by
 * {@link EntityMapper} onto ordinary documents — no JPA runtime and no relational engine are
 * involved. {@code @UuidGenerator} (id), {@code @CreationTimestamp} / {@code @UpdateTimestamp}
 * (audit fields) and {@code @Formula} (derived values) are applied at mapping time.</p>
 *
 * <p>Writes go through an MVCC transaction; aggregated reads are computed from documents by the
 * application, because no relational GROUP BY / JOIN engine is shipped.</p>
 */
public class OrderInvoiceService {

    private final EmbedJNoSQL db;
    private final DocumentCollection customers;
    private final DocumentCollection invoices;

    public OrderInvoiceService(EmbedJNoSQL db) {
        this.db = db;
        this.customers = db.documentCollection(EntityMapper.getCollectionName(CustomerAccount.class));
        this.invoices = db.documentCollection(EntityMapper.getCollectionName(InvoiceRecord.class));
    }

    public CustomerAccount registerCustomer(String id, String name, String email, String tier, double credit) {
        CustomerAccount account = new CustomerAccount(id, name, email, tier, credit);
        Document doc = EntityMapper.toDocument(account);
        try (var tx = db.beginTransaction()) {
            tx.documentCollection(EntityMapper.getCollectionName(CustomerAccount.class)).insert(doc);
            tx.commit();
        }
        return account;
    }

    public InvoiceRecord createInvoice(String customerId, double amount, InvoiceStatus status) {
        InvoiceRecord invoice = new InvoiceRecord(customerId, amount, status);
        // toDocument() assigns @UuidGenerator ids, fills @CreationTimestamp/@UpdateTimestamp,
        // and evaluates @Formula("amount * 1.20") onto both the entity and the document.
        Document doc = EntityMapper.toDocument(invoice);
        try (var tx = db.beginTransaction()) {
            tx.documentCollection(EntityMapper.getCollectionName(InvoiceRecord.class)).insert(doc);
            tx.commit();
        }
        return invoice;
    }

    public CustomerAccount findCustomer(String id) {
        Document doc = customers.findById(id);
        return doc == null ? null : EntityMapper.fromDocument(doc, CustomerAccount.class);
    }

    public InvoiceRecord findInvoice(String id) {
        Document doc = invoices.findById(id);
        return doc == null ? null : EntityMapper.fromDocument(doc, InvoiceRecord.class);
    }

    public List<InvoiceRecord> findInvoicesByCustomer(String customerId) {
        return invoices.find(Query.eq("customer_id", customerId)
                        .sortBy("created_at", Query.SortOrder.DESC))
                .stream()
                .map(doc -> EntityMapper.fromDocument(doc, InvoiceRecord.class))
                .collect(Collectors.toList());
    }

    /**
     * Invoice totals grouped by status. Computed from documents — the product ships no
     * relational GROUP BY engine, so the grouping is explicit here.
     */
    public List<Map<String, Object>> getInvoiceSummary() {
        Map<Object, List<Document>> byStatus = new LinkedHashMap<>();
        for (Document doc : invoices.findAll()) {
            byStatus.computeIfAbsent(doc.getRaw("status"), k -> new ArrayList<>()).add(doc);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Object, List<Document>> entry : byStatus.entrySet()) {
            List<Document> group = entry.getValue();
            double total = 0;
            for (Document doc : group) {
                Object amount = doc.getRaw("amount");
                if (amount instanceof Number n) total += n.doubleValue();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.getKey());
            row.put("count", (long) group.size());
            row.put("total_amount", total);
            row.put("avg_amount", group.isEmpty() ? 0.0 : total / group.size());
            rows.add(row);
        }
        rows.sort((a, b) -> Double.compare(
                ((Number) b.get("total_amount")).doubleValue(),
                ((Number) a.get("total_amount")).doubleValue()));
        return rows;
    }

    /**
     * Customers with their invoices, matched in the application through the stored
     * {@code customer_id} reference. A document reference is an id, not a foreign key: nothing
     * enforces it, and a missing customer yields null fields rather than an error.
     */
    public List<Map<String, Object>> getCustomerInvoiceDetails() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document invoice : invoices.findAll()) {
            Document customer = customers.findById(String.valueOf(invoice.getRaw("customer_id")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customer_id", invoice.getRaw("customer_id"));
            row.put("full_name", customer != null ? customer.getRaw("full_name") : null);
            row.put("loyalty_tier", customer != null ? customer.getRaw("loyalty_tier") : null);
            row.put("invoice_id", invoice.getId());
            row.put("amount", invoice.getRaw("amount"));
            row.put("total_with_tax", invoice.getRaw("total_with_tax"));
            row.put("status", invoice.getRaw("status"));
            rows.add(row);
        }
        rows.sort((a, b) -> Double.compare(
                ((Number) b.get("amount")).doubleValue(),
                ((Number) a.get("amount")).doubleValue()));
        return rows;
    }

    /** The database is owned by the caller; this service holds no resources of its own. */
    public void close() {
        // nothing to release
    }
}
