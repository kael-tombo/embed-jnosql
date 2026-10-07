package org.embeddedjnosql.db.demo.micronaut;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller("/api/orders")
public class OrderController {

    private final EmbedJNoSQL db;
    private final DocumentCollection orderCollection;
    private final ColumnFamily inventoryFamily;

    @Inject
    public OrderController(EmbedJNoSQL db) {
        this.db = db;
        this.orderCollection = db.documentCollection("orders");
        this.inventoryFamily = db.columnFamily("inventory");
        initInventoryIfEmpty();
    }

    private void initInventoryIfEmpty() {
        if (inventoryFamily.get("prod-101", "available") == null) {
            inventoryFamily.put("prod-101", "available", 50);
            inventoryFamily.put("prod-101", "warehouse", "US-EAST");
            inventoryFamily.put("prod-102", "available", 30);
            inventoryFamily.put("prod-102", "warehouse", "US-WEST");
            inventoryFamily.put("prod-103", "available", 100);
            inventoryFamily.put("prod-103", "warehouse", "EU-CENTRAL");
        }
    }

    @Get
    public List<Order> getAllOrders() {
        return orderCollection.findAll().stream()
                .map(Order::fromDocument)
                .collect(Collectors.toList());
    }

    @Get("/{id}")
    public HttpResponse<Order> getOrderById(@PathVariable String id) {
        Document doc = orderCollection.findById(id);
        if (doc == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(Order.fromDocument(doc));
    }

    @Post
    public HttpResponse<?> placeOrder(@Body Order order) {
        String orderId = order.id() != null && !order.id().isBlank()
                ? order.id()
                : "ord-" + System.currentTimeMillis();

        Order toSave = new Order(
                orderId,
                order.orderNumber() != null ? order.orderNumber() : "ORD-M-" + System.currentTimeMillis(),
                order.customerId(),
                order.items(),
                order.totalAmount(),
                "CONFIRMED",
                order.createdAt() > 0 ? order.createdAt() : System.currentTimeMillis()
        );

        Transaction tx = db.beginTransaction();
        try {
            if (toSave.items() != null) {
                for (OrderItem item : toSave.items()) {
                    Object stockVal = inventoryFamily.get(item.productId(), "available");
                    int currentStock = stockVal != null ? ((Number) stockVal).intValue() : 0;
                    if (currentStock < item.quantity()) {
                        tx.rollback();
                        return HttpResponse.badRequest(Map.of("error", "Insufficient stock for " + item.productId()));
                    }
                    inventoryFamily.put(item.productId(), "available", currentStock - item.quantity());
                }
            }

            orderCollection.insert(toSave.toDocument());
            tx.commit();
            return HttpResponse.created(toSave);
        } catch (Exception e) {
            tx.rollback();
            return HttpResponse.serverError(Map.of("error", e.getMessage()));
        }
    }

    @Get("/inventory/{productId}")
    public HttpResponse<Map<String, Object>> getInventory(@PathVariable String productId) {
        Object stock = inventoryFamily.get(productId, "available");
        if (stock == null) {
            return HttpResponse.notFound();
        }
        Object warehouse = inventoryFamily.get(productId, "warehouse");
        return HttpResponse.ok(Map.of("productId", productId, "available", stock, "warehouse", warehouse));
    }
}
