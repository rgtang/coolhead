package dev.you.coolhead.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.you.coolhead.audit.AuditLog;
import dev.you.coolhead.audit.AuditService;
import dev.you.coolhead.audit.AuditService.MonthlyStats;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.policy.TrustScoreService;

/** Dashboard data: audit trail, "this month Coolhead stopped $X", trust scores, recent orders. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final TrustScoreService trust;
    private final OrderRepository orders;

    public AuditController(AuditService audit, TrustScoreService trust, OrderRepository orders) {
        this.audit = audit;
        this.trust = trust;
        this.orders = orders;
    }

    @GetMapping("/logs")
    public List<AuditLog> logs() {
        return audit.recent();
    }

    @GetMapping("/logs/{orderId}")
    public List<AuditLog> logsForOrder(@PathVariable String orderId) {
        return audit.forOrder(orderId);
    }

    @GetMapping("/stats")
    public MonthlyStats stats() {
        return audit.monthlyStats();
    }

    @GetMapping("/trust")
    public Map<String, Integer> trust() {
        return trust.snapshot();
    }

    @GetMapping("/orders")
    public List<OrderView> orders() {
        return orders.findTop50ByOrderByCreatedAtDesc().stream().map(o -> OrderView.of(o, null)).toList();
    }
}
