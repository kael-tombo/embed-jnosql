package org.embeddedjnosql.db.demo.model;

public record InventoryItem(
        String productId,
        int available,
        int reserved
) {
    public int total() {
        return available + reserved;
    }
}
