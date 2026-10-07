package org.junify.db.demo.annotation.repository;

import org.junify.db.adapter.jnosql.JunifyRepository;
import org.junify.db.demo.annotation.model.CatalogProduct;
import org.junify.db.nosql.document.Query;

import java.util.List;

/**
 * Standard repository implementation for CatalogProduct demonstrating Eclipse JNoSQL /
 * Spring Data-like repository patterns over JunifyDB. Every finder is a native document
 * predicate — there is no text query language behind it.
 */
public class CatalogProductRepository extends JunifyRepository<CatalogProduct, String> {

    public CatalogProductRepository(org.junify.db.JunifyDB db) {
        super(CatalogProduct.class, db);
    }

    public List<CatalogProduct> findByCategory(String category) {
        return findBy("category", category);
    }

    public List<CatalogProduct> findInStock(int minStock) {
        return findByQuery(Query.gte("stock_qty", minStock)
                .sortBy("unit_price", Query.SortOrder.ASC));
    }

    public List<CatalogProduct> findByPriceBetween(double minPrice, double maxPrice) {
        return findByQuery(Query.between("unit_price", minPrice, maxPrice)
                .sortBy("unit_price", Query.SortOrder.ASC));
    }

    public List<CatalogProduct> findByFluentCategory(String category, double maxPrice) {
        return db.from(CatalogProduct.class)
                .where("category = ? AND unit_price <= ?", category, maxPrice)
                .orderBy("unit_price ASC")
                .list();
    }
}
