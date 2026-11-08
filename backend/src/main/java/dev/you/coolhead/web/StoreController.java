package dev.you.coolhead.web;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.you.coolhead.order.Product;
import dev.you.coolhead.order.ProductCatalog;

/** The fake store: a handful of hand-picked products from products.json. */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final ProductCatalog catalog;

    public StoreController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public List<Product> products() {
        return catalog.all();
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable String id) {
        return catalog.find(id).orElseThrow(() -> new NoSuchElementException("product not found: " + id));
    }
}
