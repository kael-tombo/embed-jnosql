package org.embeddedjnosql.db.demo.annotation.model;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;

/**
 * Catalog product entity modeled using standard Eclipse JNoSQL (Jakarta NoSQL) annotations.
 */
@Entity("catalog_products")
public class CatalogProduct {

    @Id
    private String id;

    @Column("sku")
    private String sku;

    @Column("title")
    private String title;

    @Column("category")
    private String category;

    @Column("unit_price")
    private Double unitPrice;

    @Column("stock_qty")
    private Integer stockQty;

    public CatalogProduct() {}

    public CatalogProduct(String id, String sku, String title, String category, Double unitPrice, Integer stockQty) {
        this.id = id;
        this.sku = sku;
        this.title = title;
        this.category = category;
        this.unitPrice = unitPrice;
        this.stockQty = stockQty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getStockQty() {
        return stockQty;
    }

    public void setStockQty(Integer stockQty) {
        this.stockQty = stockQty;
    }

    @Override
    public String toString() {
        return "CatalogProduct{" +
                "id='" + id + '\'' +
                ", sku='" + sku + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", unitPrice=" + unitPrice +
                ", stockQty=" + stockQty +
                '}';
    }
}
