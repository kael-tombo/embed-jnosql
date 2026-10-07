package org.embeddedjnosql.db.demo.micronaut;

import io.micronaut.serde.annotation.SerdeImport;
import org.embeddedjnosql.db.demo.model.Customer;
import org.embeddedjnosql.db.demo.model.InventoryItem;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.demo.model.Product;

@SerdeImport(Product.class)
@SerdeImport(Order.class)
@SerdeImport(OrderItem.class)
@SerdeImport(Customer.class)
@SerdeImport(InventoryItem.class)
public class SerdeConfiguration {
}
