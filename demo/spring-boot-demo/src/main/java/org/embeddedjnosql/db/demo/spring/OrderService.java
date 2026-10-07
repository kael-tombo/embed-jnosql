package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    private final EmbedJNoSQL db;

    public OrderService(EmbedJNoSQL db) {
        this.db = db;
    }

    public Order placeOrder(Order order) {
        String orderId = order.id() != null ? order.id() : "ord-" + UUID.randomUUID().toString().substring(0, 8);
        String orderNumber = "ORD-" + System.currentTimeMillis();

        double total = order.items().stream().mapToDouble(OrderItem::subtotal).sum();
        Order finalized = new Order(
                orderId,
                orderNumber,
                order.customerId(),
                order.items(),
                total,
                "CONFIRMED",
                System.currentTimeMillis()
        );

        // Transactional commit across orders and audit log
        try (Transaction tx = db.beginTransaction()) {
            tx.write("orders", finalized.id(), finalized.toDocument().toJson());
            tx.write("audit_events", "audit-" + System.currentTimeMillis(),
                    "{\"action\":\"ORDER_PLACED\",\"orderId\":\"" + finalized.id() + "\",\"total\":" + total + "}");
            tx.commit();
        }

        return finalized;
    }

    public Order getOrder(String id) {
        Document doc = db.documentCollection("orders").findById(id);
        return Order.fromDocument(doc);
    }
}
