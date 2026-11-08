package dev.you.coolhead.order;

import java.math.BigDecimal;

/** Static catalog item loaded from products.json. */
public record Product(
        String id,
        String name,
        String category,
        BigDecimal price,
        String image,
        String blurb) {
}
