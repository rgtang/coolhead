package dev.you.coolhead.web;

import java.util.NoSuchElementException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.you.coolhead.agent.InterrogationService;
import dev.you.coolhead.agent.Verdict;
import dev.you.coolhead.agent.VerdictService;
import dev.you.coolhead.audit.AuditLog.Trigger;
import dev.you.coolhead.audit.AuditService;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.order.OrderState;
import dev.you.coolhead.order.OrderStateMachine;
import dev.you.coolhead.order.PurchaseOrder;
import dev.you.coolhead.policy.TrustScoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Conversation turns, final verdict, and the human decisions that actually move money.
 * <p>
 * Note the split: {@code /message} and {@code /verdict} only produce text/advice. {@code /accept}, {@code /override},
 * {@code /confirm}, {@code /cancel} are the user's clicks — the only non-timer way to capture or void.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final OrderRepository orders;
    private final InterrogationService interrogation;
    private final VerdictService verdicts;
    private final OrderStateMachine stateMachine;
    private final TrustScoreService trust;
    private final AuditService audit;

    public AgentController(OrderRepository orders, InterrogationService interrogation, VerdictService verdicts,
                           OrderStateMachine stateMachine, TrustScoreService trust, AuditService audit) {
        this.orders = orders;
        this.interrogation = interrogation;
        this.verdicts = verdicts;
        this.stateMachine = stateMachine;
        this.trust = trust;
        this.audit = audit;
    }

    @GetMapping("/{orderId}")
    public OrderView get(@PathVariable String orderId) {
        PurchaseOrder o = load(orderId);
        return OrderView.of(o, interrogation.transcript(o));
    }

    // ---- Layer 2: conversation --------------------------------------------------------------------

    @PostMapping("/{orderId}/message")
    public MessageResponse message(@PathVariable String orderId, @Valid @RequestBody MessageRequest req) {
        PurchaseOrder o = load(orderId);
        requireState(o, OrderState.AUTHORIZED, "chat");
        InterrogationService.Reply r = interrogation.reply(o, req.text().trim());
        return new MessageResponse(r.question(), r.done(), r.urgencySignal(), OrderView.of(load(orderId), r.transcript()));
    }

    /** Final review. Stores advice on the order; changes nothing about the payment. */
    @PostMapping("/{orderId}/verdict")
    public OrderView verdict(@PathVariable String orderId) {
        PurchaseOrder o = load(orderId);
        requireState(o, OrderState.AUTHORIZED, "judge");
        Verdict v = verdicts.decide(o);
        audit.record(o.getId(), Trigger.USER_CLICK, AuditService.ACTION_VERDICT,
                "%s %dh — buy: %s | wait: %s".formatted(v.action(), v.coolingHours(), v.reasonToBuy(), v.reasonToWait()),
                o.getAmount(), o.getCategory());
        return get(orderId);
    }

    // ---- Human decisions (USER_CLICK) --------------------------------------------------------------

    /** User accepts the verdict card as-is: RELEASE -> capture, COOL -> start cooling, BLOCK -> void. */
    @PostMapping("/{orderId}/accept")
    public OrderView accept(@PathVariable String orderId) {
        PurchaseOrder o = load(orderId);
        requireState(o, OrderState.AUTHORIZED, "accept verdict");
        if (o.getVerdictAction() == null) throw new IllegalStateException("No verdict yet for order " + orderId);

        switch (Verdict.Action.valueOf(o.getVerdictAction())) {
            case RELEASE -> {
                stateMachine.capture(o, Trigger.USER_CLICK, "accepted RELEASE verdict");
                trust.onClearRelease(o.getCategory());
            }
            case COOL -> stateMachine.startCooling(o,
                    o.getVerdictCoolingHours() == null || o.getVerdictCoolingHours() <= 0 ? 24 : o.getVerdictCoolingHours(),
                    Trigger.USER_CLICK, "accepted COOL verdict");
            case BLOCK -> {
                stateMachine.voidOrder(o, Trigger.USER_CLICK, "accepted BLOCK verdict");
                trust.onAcceptBlock(o.getCategory());
            }
        }
        return get(orderId);
    }

    /** User insists, with a written reason. Allowed from AUTHORIZED or COOLING. Costs trust. */
    @PostMapping("/{orderId}/override")
    public OrderView override(@PathVariable String orderId, @Valid @RequestBody OverrideRequest req) {
        PurchaseOrder o = load(orderId);
        stateMachine.override(o, req.reason());
        trust.onOverride(o.getCategory());
        return get(orderId);
    }

    /** Cooling finished and the user still wants it: capture. */
    @PostMapping("/{orderId}/confirm")
    public OrderView confirm(@PathVariable String orderId) {
        PurchaseOrder o = load(orderId);
        requireState(o, OrderState.COOLING, "confirm");
        if (!o.coolingElapsed()) {
            throw new IllegalStateException("Cooling period has not ended yet (until " + o.getCoolingUntil() + ")");
        }
        stateMachine.capture(o, Trigger.USER_CLICK, "confirmed after cooling");
        trust.onConfirmAfterCooling(o.getCategory());
        return get(orderId);
    }

    /** User changed their mind at any point before capture: release the hold. */
    @PostMapping("/{orderId}/cancel")
    public OrderView cancel(@PathVariable String orderId) {
        PurchaseOrder o = load(orderId);
        boolean wasCooling = o.getState() == OrderState.COOLING;
        stateMachine.voidOrder(o, Trigger.USER_CLICK, wasCooling ? "cancelled during/after cooling" : "cancelled before verdict");
        if (wasCooling) trust.onCancelAfterCooling(o.getCategory());
        return get(orderId);
    }

    // -----------------------------------------------------------------------------------------------

    private PurchaseOrder load(String id) {
        return orders.findById(id).orElseThrow(() -> new NoSuchElementException("order not found: " + id));
    }

    private static void requireState(PurchaseOrder o, OrderState expected, String op) {
        if (o.getState() != expected) {
            throw new IllegalStateException("Cannot %s: order is %s".formatted(op, o.getState()));
        }
    }

    public record MessageRequest(@NotBlank @Size(max = 1000) String text) { }

    public record MessageResponse(String question, boolean done, String urgencySignal, OrderView order) { }

    public record OverrideRequest(@NotBlank @Size(min = 10, max = 500) String reason) { }
}
