package org.junify.db.demo.spring;

import org.junify.db.demo.model.Product;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;
import org.junify.db.spring.boot.JunifyDBTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final JunifyDBTemplate template;
    private static final String COLLECTION = "products";

    public ProductService(JunifyDBTemplate template) {
        this.template = template;
        template.documents(COLLECTION).createIndex("category");
    }

    private DocumentCollection collection() {
        return template.documents(COLLECTION);
    }

    public Product save(Product product) {
        Document saved = collection().insert(product.toDocument());
        return Product.fromDocument(saved);
    }

    public Optional<Product> findById(String id) {
        Document doc = collection().findById(id);
        return Optional.ofNullable(Product.fromDocument(doc));
    }

    public List<Product> findAll() {
        return collection().findAll()
                .stream().map(Product::fromDocument).collect(Collectors.toList());
    }

    public List<Product> findByCategory(String category) {
        return collection().find(Query.eq("category", category))
                .stream().map(Product::fromDocument).collect(Collectors.toList());
    }

    public long count() {
        return collection().count();
    }

    /**
     * Documents filtered by category and an inclusive price range, cheapest first.
     * Expressed as a native document predicate — the fluent/NoSQL query path the
     * product ships.
     */
    public List<Product> findByCategoryAndPriceRange(String category, double minPrice, double maxPrice) {
        Query q = Query.eq("category", category)
                .and(Query.gte("price", minPrice))
                .and(Query.lte("price", maxPrice))
                .sortBy("price", Query.SortOrder.ASC);
        return collection().find(q).stream().map(Product::fromDocument).collect(Collectors.toList());
    }
}
