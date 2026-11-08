package dev.you.coolhead.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.you.coolhead.audit.AuditLog.Trigger;
import dev.you.coolhead.config.PolicyProperties;
import dev.you.coolhead.policy.TrustScoreService;

/**
 * Periodically scans COOLING orders whose window has elapsed.
 * <ol>
 *   <li>First pass after expiry: emit a REMINDER (the frontend polls and shows "ready to decide").</li>
 *   <li>If still undecided after {@code voidGraceHours}: void the authorization (trigger TIMER).</li>
 * </ol>
 * Doing nothing is a decision — and it is the safe one, because the money stays with the buyer.
 */
@Component
public class CoolingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoolingScheduler.class);

    private final OrderRepository orders;
    private final OrderStateMachine stateMachine;
    private final TrustScoreService trust;
    private final PolicyProperties policy;

    public CoolingScheduler(OrderRepository orders, OrderStateMachine stateMachine,
                            TrustScoreService trust, PolicyProperties policy) {
        this.orders = orders;
        this.stateMachine = stateMachine;
        this.trust = trust;
        this.policy = policy;
    }

    @Scheduled(fixedDelayString = "${coolhead.scheduler.scan-interval-ms:60000}", initialDelay = 15_000)
    public void scan() {
        Instant now = Instant.now();
        List<PurchaseOrder> due = orders.findByStateAndCoolingUntilBefore(OrderState.COOLING, now);
        if (due.isEmpty()) return;

        Duration grace = Duration.ofHours(policy.voidGraceHours());
        for (PurchaseOrder order : due) {
            try {
                if (order.getRemindedAt() == null) {
                    stateMachine.markReminded(order);
                } else if (order.getRemindedAt().plus(grace).isBefore(now)) {
                    stateMachine.voidOrder(order, Trigger.TIMER,
                            "no decision within %dh after cooling ended".formatted(policy.voidGraceHours()));
                    trust.onTimerVoid(order.getCategory());
                }
            } catch (Exception e) {
                log.error("scheduler failed on order {}", order.getId(), e);
            }
        }
    }
}
