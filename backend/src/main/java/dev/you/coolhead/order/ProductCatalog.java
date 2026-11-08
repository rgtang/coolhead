package dev.you.coolhead.order;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Loads the fake store's catalog once from classpath:products.json. */
@Component
public class ProductCatalog {

    private final List<Product> products;
    private final Map<String, Product> byId;

    public ProductCatalog(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("products.json").getInputStream()) {
            this.products = List.copyOf(mapper.readValue(in, new TypeReference<List<Product>>() { }));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load products.json", e);
        }
        this.byId = products.stream().collect(Collectors.toMap(Product::id, Function.identity()));
    }

    public List<Product> all() {
        return products;
    }

    public Optional<Product> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Product require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown product: " + id));
    }
}
