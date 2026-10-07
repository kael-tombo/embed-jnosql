package org.embeddedjnosql.db.demo.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Product;
import org.embeddedjnosql.db.demo.model.SampleData;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ProductResource {

    private final DocumentCollection productCollection;
    private final KeyValueBucket priceCache;

    @Inject
    public ProductResource(EmbedJNoSQL db) {
        this.productCollection = db.documentCollection("products");
        this.priceCache = db.keyValueBucket("price_cache");
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

    @GET
    public List<Product> getAllProducts() {
        return productCollection.findAll().stream()
                .map(Product::fromDocument)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public Response getProductById(@PathParam("id") String id) {
        Document doc = productCollection.findById(id);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Product.fromDocument(doc)).build();
    }

    @POST
    public Response createProduct(Product product) {
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
        return Response.status(Response.Status.CREATED).entity(toSave).build();
    }

    @GET
    @Path("/category/{category}")
    public List<Product> getProductsByCategory(@PathParam("category") String category) {
        return productCollection.find(Query.eq("category", category)).stream()
                .map(Product::fromDocument)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}/cached-price")
    public Response getCachedPrice(@PathParam("id") String id) {
        String price = priceCache.get(id);
        if (price == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok("{\"id\":\"" + id + "\",\"cachedPrice\":" + price + "}").build();
    }
}
