package dev.you.coolhead.order;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.you.coolhead.audit.AuditLog.Trigger;
import dev.you.coolhead.audit.AuditService;
import dev.you.coolhead.config.PolicyProperties;
import dev.you.coolhead.paypal.PayPalClient;
import dev.you.coolhead.paypal.dto.CaptureResponse;

/**
 * The ONLY place that changes {@link PurchaseOrder#getState()} or talks to PayPal capture/void.
 * <p>
 * Every transition requires an explicit {@link Trigger} (TIMER or USER_CLICK) so the audit trail can prove that
 * no LLM output ever moved money on its own. Cooling duration is clamped here, not where it was proposed.
 */
@Service
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    private static final Set<OrderState> CAN_CAPTURE = EnumSet.of(OrderState.AUTHORIZED, OrderState.COOLING);
    private static final Set<OrderState> CAN_VOID = EnumSet.of(OrderState.AUTHORIZED, OrderState.COOLING);
    private static final Set<OrderState> CAN_COOL = EnumSet.of(OrderState.AUTHORIZED);

    private final OrderRepository orders;
    private final PayPalClient paypal;
    private final AuditService audit;
    private final PolicyProperties policy;

    public OrderStateMachine(OrderRepository orders, PayPalClient paypal, AuditService audit, PolicyProperties policy) {
        this.orders = orders;
        this.paypal = paypal;
        this.audit = audit;
        this.policy = policy;
    }

    /** AUTHORIZED -> COOLING. {@code requestedHours} is clamped to [min,max]. */
    @Transactional
    public PurchaseOrder startCooling(PurchaseOrder stale, int requestedHours, Trigger trigger, String detail) {
        PurchaseOrder order = fresh(stale);
        require(order, CAN_COOL, "start cooling");
        int hours = policy.clampCoolingHours(requestedHours);
        Instant until = Instant.now().plus(Duration.ofHours(hours));
        order.setCooling(hours, until);
        order.setState(OrderState.COOLING);
        PurchaseOrder saved = orders.save(order);
        audit.record(order.getId(), trigger, AuditService.ACTION_COOLING_STARTED,
                "cooling %dh (requested %dh) until %s — %s".formatted(hours, requestedHours, until, detail),
                order.getAmount(), order.getCategory());
        log.info("order {} -> COOLING for {}h", order.getId(), hours);
        return saved;
    }

    /** AUTHORIZED|COOLING -> CAPTURED. Money actually moves here. */
    @Transactional
    public PurchaseOrder capture(PurchaseOrder stale, Trigger trigger, String detail) {
        PurchaseOrder order = fresh(stale);
        require(order, CAN_CAPTURE, "capture");
        CaptureResponse res = paypal.captureAuthorization(order.getPaypalAuthorizationId());
        order.setPaypalCaptureId(res != null ? res.id() : null);
        order.setState(OrderState.CAPTURED);
        PurchaseOrder saved = orders.save(order);
        audit.record(order.getId(), trigger, AuditService.ACTION_CAPTURED, detail, order.getAmount(), order.getCategory());
        log.info("order {} -> CAPTURED ({})", order.getId(), detail);
        return saved;
    }

    /** Capture after the user supplied an explicit override reason. Recorded separately for the dashboard. */
    @Transactional
    public PurchaseOrder override(PurchaseOrder stale, String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Override reason must be at least 10 characters");
        }
        PurchaseOrder order = fresh(stale);
        require(order, CAN_CAPTURE, "override");
        order.setOverrideReason(reason.trim());
        audit.record(order.getId(), Trigger.USER_CLICK, AuditService.ACTION_OVERRIDE, reason.trim(),
                order.getAmount(), order.getCategory());
        return capture(order, Trigger.USER_CLICK, "override: " + reason.trim());
    }

    /** AUTHORIZED|COOLING -> VOIDED. The hold is released back to the buyer. */
    @Transactional
    public PurchaseOrder voidOrder(PurchaseOrder stale, Trigger trigger, String detail) {
        PurchaseOrder order = fresh(stale);
        require(order, CAN_VOID, "void");
        paypal.voidAuthorization(order.getPaypalAuthorizationId());
        order.setState(OrderState.VOIDED);
        PurchaseOrder saved = orders.save(order);
        audit.record(order.getId(), trigger, AuditService.ACTION_VOIDED, detail, order.getAmount(), order.getCategory());
        log.info("order {} -> VOIDED ({})", order.getId(), detail);
        return saved;
    }

    /** COOLING: mark that the "your cooling period is over" reminder was emitted (TIMER only). */
    @Transactional
    public PurchaseOrder markReminded(PurchaseOrder stale) {
        PurchaseOrder order = fresh(stale);
        require(order, EnumSet.of(OrderState.COOLING), "remind");
        order.setRemindedAt(Instant.now());
        PurchaseOrder saved = orders.save(order);
        audit.record(order.getId(), Trigger.TIMER, AuditService.ACTION_REMINDER,
                "cooling ended; waiting %dh for a decision before auto-void".formatted(policy.voidGraceHours()),
                order.getAmount(), order.getCategory());
        return saved;
    }

    /**
     * Always operate on the current row. Callers often hold an instance that another service already saved
     * (verdict, transcript), and {@code @Version} would otherwise reject the stale copy.
     */
    private PurchaseOrder fresh(PurchaseOrder stale) {
        return orders.findById(stale.getId())
                .orElseThrow(() -> new IllegalStateException("order vanished: " + stale.getId()));
    }

    private static void require(PurchaseOrder order, Set<OrderState> allowed, String op) {
        if (!allowed.contains(order.getState())) {
            throw new IllegalStateException(
                    "Cannot %s order %s in state %s".formatted(op, order.getId(), order.getState()));
        }
    }
}
