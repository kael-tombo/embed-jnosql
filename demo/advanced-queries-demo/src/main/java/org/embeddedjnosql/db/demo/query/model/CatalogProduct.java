package org.embeddedjnosql.db.demo.query.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_products")
public class CatalogProduct {
    @Id
    private String id;
    private String name;
    private String category;
    private double price;
    private double rating;
    private int inStock;

    public CatalogProduct() {}

    public CatalogProduct(String id, String name, String category, double price, double rating, int inStock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.rating = rating;
        this.inStock = inStock;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getInStock() { return inStock; }
    public void setInStock(int inStock) { this.inStock = inStock; }
}
