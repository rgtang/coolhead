package dev.you.coolhead.agent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.you.coolhead.audit.AuditService;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.order.OrderState;
import dev.you.coolhead.order.PurchaseOrder;
import dev.you.coolhead.policy.TrustScoreService;

/**
 * Computes hard numbers server-side so the LLM reasons over facts it cannot hallucinate or be talked out of:
 * monthly spend, repeat purchases, hour of day, category trust, recent overrides.
 */
@Service
public class FactCollector {

    private static final ZoneId USER_ZONE = ZoneId.systemDefault();

    private final OrderRepository orders;
    private final TrustScoreService trust;
    private final AuditService audit;

    public FactCollector(OrderRepository orders, TrustScoreService trust, AuditService audit) {
        this.orders = orders;
        this.trust = trust;
        this.audit = audit;
    }

    public Facts collect(PurchaseOrder order) {
        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(USER_ZONE).withDayOfMonth(1).atStartOfDay(USER_ZONE).toInstant();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));

        List<PurchaseOrder> capturedThisMonth = orders.findByStateAndCreatedAtAfter(OrderState.CAPTURED, monthStart);
        BigDecimal monthlySpend = sum(capturedThisMonth);
        BigDecimal monthlySpendSameCategory = sum(capturedThisMonth.stream()
                .filter(o -> o.getCategory().equalsIgnoreCase(order.getCategory())).toList());

        long sameProduct30d = orders.findByProductIdAndCreatedAtAfter(order.getProductId(), thirtyDaysAgo).stream()
                .filter(o -> !o.getId().equals(order.getId()))
                .count();
        long sameCategory30d = orders.findByCategoryAndCreatedAtAfter(order.getCategory(), thirtyDaysAgo).stream()
                .filter(o -> !o.getId().equals(order.getId()))
                .count();

        LocalTime localTime = LocalTime.now(USER_ZONE);
        int hour = localTime.getHour();
        boolean lateNight = hour >= 23 || hour < 6;

        return new Facts(
                order.getProductName(),
                order.getCategory(),
                order.getAmount(),
                order.getCurrency(),
                monthlySpend,
                monthlySpendSameCategory,
                sameProduct30d,
                sameCategory30d,
                hour,
                lateNight,
                trust.get(order.getCategory()),
                audit.countSince(AuditService.ACTION_OVERRIDE, thirtyDaysAgo),
                audit.countSince(AuditService.ACTION_VOIDED, thirtyDaysAgo));
    }

    private static BigDecimal sum(List<PurchaseOrder> list) {
        return list.stream().map(PurchaseOrder::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Facts(
            String productName,
            String category,
            BigDecimal amount,
            String currency,
            BigDecimal monthlySpend,
            BigDecimal monthlySpendSameCategory,
            long sameProductLast30d,
            long sameCategoryLast30d,
            int localHour,
            boolean lateNight,
            int categoryTrustScore,
            long overridesLast30d,
            long voidsLast30d) {

        /** Compact, LLM-friendly rendering. */
        public String render() {
            return """
                    FACTS (computed by server, trustworthy):
                    - item: %s  (%s)  amount: %s %s
                    - spent this month (captured): %s total, %s in this category
                    - bought the same item in last 30 days: %d times
                    - bought in this category in last 30 days: %d times
                    - local time: %02d:00%s
                    - trust score for this category: %d/100
                    - overrides in last 30 days: %d; voided (changed mind) in last 30 days: %d
                    """.formatted(productName, category, amount.toPlainString(), currency,
                    monthlySpend.toPlainString(), monthlySpendSameCategory.toPlainString(),
                    sameProductLast30d, sameCategoryLast30d,
                    localHour, lateNight ? " (late night)" : "",
                    categoryTrustScore, overridesLast30d, voidsLast30d);
        }
    }
}
