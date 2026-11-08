package dev.you.coolhead.order;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {

    List<PurchaseOrder> findByStateAndCoolingUntilBefore(OrderState state, Instant before);

    List<PurchaseOrder> findByStateAndCreatedAtAfter(OrderState state, Instant after);

    List<PurchaseOrder> findByProductIdAndCreatedAtAfter(String productId, Instant after);

    List<PurchaseOrder> findByCategoryAndCreatedAtAfter(String category, Instant after);

    List<PurchaseOrder> findTop50ByOrderByCreatedAtDesc();
}
