package dev.you.coolhead.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dev.you.coolhead.agent.InterrogationService.Turn;
import dev.you.coolhead.order.OrderState;
import dev.you.coolhead.order.PurchaseOrder;

/** JSON shape the frontend consumes for an order. */
public record OrderView(
        String id,
        String productId,
        String productName,
        String category,
        BigDecimal amount,
        String currency,
        OrderState state,
        Integer coolingHours,
        Instant coolingUntil,
        boolean coolingElapsed,
        Instant remindedAt,
        VerdictView verdict,
        String overrideReason,
        int interrogationTurns,
        List<Turn> transcript,
        String paypalOrderId,
        String paypalAuthorizationId,
        String paypalCaptureId,
        Instant createdAt,
        Instant updatedAt) {

    public record VerdictView(String action, String reasonToBuy, String reasonToWait, String source, Integer coolingHours) {
    }

    public static OrderView of(PurchaseOrder o, List<Turn> transcript) {
        VerdictView v = o.getVerdictAction() == null ? null
                : new VerdictView(o.getVerdictAction(), o.getReasonToBuy(), o.getReasonToWait(),
                        o.getVerdictSource(), o.getVerdictCoolingHours());
        return new OrderView(
                o.getId(), o.getProductId(), o.getProductName(), o.getCategory(), o.getAmount(), o.getCurrency(),
                o.getState(), o.getCoolingHours(), o.getCoolingUntil(), o.coolingElapsed(), o.getRemindedAt(),
                v, o.getOverrideReason(), o.getInterrogationTurns(),
                transcript == null ? List.of() : transcript.stream().filter(t -> !"system".equals(t.role())).toList(),
                o.getPaypalOrderId(), o.getPaypalAuthorizationId(), o.getPaypalCaptureId(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
