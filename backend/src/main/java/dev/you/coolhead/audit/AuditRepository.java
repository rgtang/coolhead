package dev.you.coolhead.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AuditLog> findByOrderIdOrderByCreatedAtAsc(String orderId);

    List<AuditLog> findByActionAndCreatedAtAfter(String action, Instant after);

    long countByActionAndCreatedAtAfter(String action, Instant after);
}
