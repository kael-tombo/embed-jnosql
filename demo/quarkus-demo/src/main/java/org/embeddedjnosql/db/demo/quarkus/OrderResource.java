package org.embeddedjnosql.db.demo.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OrderResource {

    private final EmbedJNoSQL db;
    private final DocumentCollection orderCollection;
    private final ColumnFamily inventoryFamily;

    @Inject
    public OrderResource(EmbedJNoSQL db) {
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

    @GET
    public List<Order> getAllOrders() {
        return orderCollection.findAll().stream()
                .map(Order::fromDocument)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public Response getOrderById(@PathParam("id") String id) {
        Document doc = orderCollection.findById(id);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Order.fromDocument(doc)).build();
    }

    @POST
    public Response placeOrder(Order order) {
        String orderId = order.id() != null && !order.id().isBlank()
                ? order.id()
                : "ord-" + System.currentTimeMillis();

        Order toSave = new Order(
                orderId,
                order.orderNumber() != null ? order.orderNumber() : "ORD-" + System.currentTimeMillis(),
                order.customerId(),
                order.items(),
                order.totalAmount(),
                "CONFIRMED",
                order.createdAt() > 0 ? order.createdAt() : System.currentTimeMillis()
        );

        // Transactional placement
        Transaction tx = db.beginTransaction();
        try {
            if (toSave.items() != null) {
                for (OrderItem item : toSave.items()) {
                    Object stockVal = inventoryFamily.get(item.productId(), "available");
                    int currentStock = stockVal != null ? ((Number) stockVal).intValue() : 0;
                    if (currentStock < item.quantity()) {
                        tx.rollback();
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\":\"Insufficient stock for product " + item.productId() + "\"}")
                                .build();
                    }
                    inventoryFamily.put(item.productId(), "available", currentStock - item.quantity());
                }
            }

            orderCollection.insert(toSave.toDocument());
            tx.commit();
            return Response.status(Response.Status.CREATED).entity(toSave).build();
        } catch (Exception e) {
            tx.rollback();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/inventory/{productId}")
    public Response getInventory(@PathParam("productId") String productId) {
        Object stock = inventoryFamily.get(productId, "available");
        if (stock == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Object warehouse = inventoryFamily.get(productId, "warehouse");
        return Response.ok("{\"productId\":\"" + productId + "\",\"available\":" + stock + ",\"warehouse\":\"" + warehouse + "\"}").build();
    }
}
