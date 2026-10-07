package org.embeddedjnosql.db.demo.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.demo.model.Product;
import org.embeddedjnosql.db.demo.model.SampleData;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vert.x reactive verticle demonstrating thread-safe, non-blocking interaction
 * with embedded EmbedJNoSQL using executeBlocking to protect the reactive event-loop.
 */
public class EcommerceVerticle extends AbstractVerticle {

    private EmbedJNoSQL db;
    private DocumentCollection productCollection;
    private DocumentCollection orderCollection;
    private KeyValueBucket priceCache;
    private ColumnFamily inventoryFamily;

    private final int httpPort;

    public EcommerceVerticle() {
        this(8084);
    }

    public EcommerceVerticle(int httpPort) {
        this.httpPort = httpPort;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // Initialize EmbedJNoSQL embedded
        this.db = EmbedJNoSQL.create(
                EmbedJNoSQLConfig.builder()
                        .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                        .autoFlush(true)
                        .buildConfig()
        );

        this.productCollection = db.documentCollection("products");
        this.orderCollection = db.documentCollection("orders");
        this.priceCache = db.keyValueBucket("price_cache");
        this.inventoryFamily = db.columnFamily("inventory");

        seedInitialData();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/api/products").handler(this::handleGetAllProducts);
        router.get("/api/products/:id").handler(this::handleGetProductById);
        router.post("/api/products").handler(this::handleCreateProduct);
        router.get("/api/products/:id/cached-price").handler(this::handleGetCachedPrice);

        router.get("/api/orders").handler(this::handleGetAllOrders);
        router.post("/api/orders").handler(this::handlePlaceOrder);
        router.get("/api/orders/inventory/:productId").handler(this::handleGetInventory);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(httpPort)
                .onSuccess(server -> startPromise.complete())
                .onFailure(startPromise::fail);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        if (db != null && db.isOpen()) {
            db.close();
        }
        stopPromise.complete();
    }

    private void seedInitialData() {
        for (Product p : SampleData.products()) {
            productCollection.insert(p.toDocument());
            priceCache.put(p.id(), String.valueOf(p.price()));
        }

        inventoryFamily.put("prod-101", "available", 50);
        inventoryFamily.put("prod-101", "warehouse", "US-EAST");
        inventoryFamily.put("prod-102", "available", 30);
        inventoryFamily.put("prod-102", "warehouse", "US-WEST");
        inventoryFamily.put("prod-103", "available", 100);
        inventoryFamily.put("prod-103", "warehouse", "EU-CENTRAL");
    }

    private void handleGetAllProducts(RoutingContext ctx) {
        vertx.<List<Product>>executeBlocking(() -> {
            return productCollection.findAll().stream()
                    .map(Product::fromDocument)
                    .collect(Collectors.toList());
        }).onSuccess(products -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(products));
        }).onFailure(ctx::fail);
    }

    private void handleGetProductById(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        vertx.<Product>executeBlocking(() -> {
            Document doc = productCollection.findById(id);
            return doc != null ? Product.fromDocument(doc) : null;
        }).onSuccess(product -> {
            if (product == null) {
                ctx.response().setStatusCode(404).end();
            } else {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encode(product));
            }
        }).onFailure(ctx::fail);
    }

    private void handleCreateProduct(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        Product product = Json.decodeValue(body.encode(), Product.class);

        vertx.<Product>executeBlocking(() -> {
            String productId = product.id() != null && !product.id().isBlank()
                    ? product.id()
                    : "prod-" + System.currentTimeMillis();

            Product toSave = new Product(
                    productId,
                    product.sku(),
                    product.name(),
                    product.category(),
                    product.price(),
                    product.tags(),
                    product.attributes()
            );

            productCollection.insert(toSave.toDocument());
            priceCache.put(toSave.id(), String.valueOf(toSave.price()));
            return toSave;
        }).onSuccess(saved -> {
            ctx.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(saved));
        }).onFailure(ctx::fail);
    }

    private void handleGetCachedPrice(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        vertx.<String>executeBlocking(() -> {
            return priceCache.get(id);
        }).onSuccess(price -> {
            if (price == null) {
                ctx.response().setStatusCode(404).end();
            } else {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("id", id).put("cachedPrice", Double.parseDouble(price)).encode());
            }
        }).onFailure(ctx::fail);
    }

    private void handleGetAllOrders(RoutingContext ctx) {
        vertx.<List<Order>>executeBlocking(() -> {
            return orderCollection.findAll().stream()
                    .map(Order::fromDocument)
                    .collect(Collectors.toList());
        }).onSuccess(orders -> {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(orders));
        }).onFailure(ctx::fail);
    }

    private void handlePlaceOrder(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        Order order = Json.decodeValue(body.encode(), Order.class);

        vertx.<Order>executeBlocking(() -> {
            String orderId = order.id() != null && !order.id().isBlank()
                    ? order.id()
                    : "ord-" + System.currentTimeMillis();

            Order toSave = new Order(
                    orderId,
                    order.orderNumber() != null ? order.orderNumber() : "ORD-V-" + System.currentTimeMillis(),
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
                            throw new IllegalStateException("Insufficient stock for " + item.productId());
                        }
                        inventoryFamily.put(item.productId(), "available", currentStock - item.quantity());
                    }
                }

                orderCollection.insert(toSave.toDocument());
                tx.commit();
                return toSave;
            } catch (Exception e) {
                tx.rollback();
                throw new RuntimeException(e);
            }
        }).onSuccess(saved -> {
            ctx.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json")
                    .end(Json.encode(saved));
        }).onFailure(err -> {
            ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(new JsonObject().put("error", err.getMessage()).encode());
        });
    }

    private void handleGetInventory(RoutingContext ctx) {
        String productId = ctx.pathParam("productId");
        vertx.<JsonObject>executeBlocking(() -> {
            Object stock = inventoryFamily.get(productId, "available");
            if (stock == null) return null;
            Object warehouse = inventoryFamily.get(productId, "warehouse");
            return new JsonObject()
                    .put("productId", productId)
                    .put("available", stock)
                    .put("warehouse", warehouse);
        }).onSuccess(result -> {
            if (result == null) {
                ctx.response().setStatusCode(404).end();
            } else {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(result.encode());
            }
        }).onFailure(ctx::fail);
    }
}
