package org.embeddedjnosql.db.demo.micronaut;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Product;
import org.embeddedjnosql.db.demo.model.SampleData;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller("/api/products")
public class ProductController {

    private final DocumentCollection productCollection;
    private final KeyValueBucket priceCache;

    @Inject
    public ProductController(EmbedJNoSQL embeddedjnosqlDB) {
        this.productCollection = embeddedjnosqlDB.documentCollection("products");
        this.priceCache = embeddedjnosqlDB.keyValueBucket("price_cache");
        seedSampleDataIfEmpty();
    }

    private void seedSampleDataIfEmpty() {
        if (productCollection.count() == 0) {
            for (Product p : SampleData.products()) {
                productCollection.insert(p.toDocument());
                priceCache.put(p.id(), String.valueOf(p.price()));
            }
        }
    }

    @Get
    public List<Product> getAllProducts() {
        return productCollection.findAll().stream()
                .map(Product::fromDocument)
                .collect(Collectors.toList());
    }

    @Get("/{id}")
    public HttpResponse<Product> getProductById(@PathVariable String id) {
        Document doc = productCollection.findById(id);
        if (doc == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(Product.fromDocument(doc));
    }

    @Post
    public HttpResponse<Product> createProduct(@Body Product product) {
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
        return HttpResponse.created(toSave);
    }

    @Get("/category/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        return productCollection.find(Query.eq("category", category)).stream()
                .map(Product::fromDocument)
                .collect(Collectors.toList());
    }

    @Get("/{id}/cached-price")
    public HttpResponse<Map<String, Object>> getCachedPrice(@PathVariable String id) {
        String price = priceCache.get(id);
        if (price == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(Map.of("id", id, "cachedPrice", Double.parseDouble(price)));
    }
}
