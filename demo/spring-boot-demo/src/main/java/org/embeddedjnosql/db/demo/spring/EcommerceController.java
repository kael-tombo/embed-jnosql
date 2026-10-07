package org.embeddedjnosql.db.demo.spring;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.Product;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EcommerceController {

    private final ProductService productService;
    private final OrderService orderService;
    private final EmbedJNoSQL db;

    public EcommerceController(ProductService productService, OrderService orderService, EmbedJNoSQL db) {
        this.productService = productService;
        this.orderService = orderService;
        this.db = db;
    }

    @GetMapping("/products")
    public List<Product> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {
        if (category != null && minPrice != null && maxPrice != null) {
            return productService.findByCategoryAndPriceRange(category, minPrice, maxPrice);
        }
        if (category != null && !category.isBlank()) {
            return productService.findByCategory(category);
        }
        return productService.findAll();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        return productService.save(product);
    }

    @PostMapping("/orders")
    public Order createOrder(@RequestBody Order order) {
        return orderService.placeOrder(order);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order ord = orderService.getOrder(id);
        return ord != null ? ResponseEntity.ok(ord) : ResponseEntity.notFound().build();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "database", db.isOpen() ? "OPEN" : "CLOSED",
                "metrics", db.metrics().snapshot()
        );
    }
}
