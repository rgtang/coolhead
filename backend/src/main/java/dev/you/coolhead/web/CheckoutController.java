package dev.you.coolhead.web;

import java.util.Map;
import java.util.Optional;

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
import dev.you.coolhead.config.PayPalProperties;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.order.OrderStateMachine;
import dev.you.coolhead.order.Product;
import dev.you.coolhead.order.ProductCatalog;
import dev.you.coolhead.order.PurchaseOrder;
import dev.you.coolhead.paypal.PayPalClient;
import dev.you.coolhead.paypal.dto.PayPalOrderResponse;
import dev.you.coolhead.policy.GuardrailService;
import dev.you.coolhead.policy.TrustScoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Checkout = PayPal create (intent AUTHORIZE) -> buyer approval in the PayPal button -> authorize ->
 * hand the held payment to the agent pipeline.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final ProductCatalog catalog;
    private final PayPalClient paypal;
    private final PayPalProperties paypalProps;
    private final OrderRepository orders;
    private final OrderStateMachine stateMachine;
    private final GuardrailService guardrail;
    private final VerdictService verdicts;
    private final InterrogationService interrogation;
    private final TrustScoreService trust;
    private final AuditService audit;

    public CheckoutController(ProductCatalog catalog, PayPalClient paypal, PayPalProperties paypalProps,
                              OrderRepository orders, OrderStateMachine stateMachine, GuardrailService guardrail,
                              VerdictService verdicts, InterrogationService interrogation,
                              TrustScoreService trust, AuditService audit) {
        this.catalog = catalog;
        this.paypal = paypal;
        this.paypalProps = paypalProps;
        this.orders = orders;
        this.stateMachine = stateMachine;
        this.guardrail = guardrail;
        this.verdicts = verdicts;
        this.interrogation = interrogation;
        this.trust = trust;
        this.audit = audit;
    }

    /** Step 1 — called by the PayPal button's createOrder(). Returns the PayPal order id. */
    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateRequest req) {
        Product product = catalog.require(req.productId());
        PayPalOrderResponse res = paypal.createOrder(product.id(), product.name(), product.price());
        return Map.of(
                "paypalOrderId", res.id(),
                "status", res.status(),
                "mock", paypal.isMock(),
                "approveUrl", res.approveLink().orElse(""));
    }

    /**
     * Step 2 — called by the PayPal button's onApprove(). Authorizes (holds) the funds, creates the local order and
     * immediately runs Layer 3 guardrails. Returns what the UI should do next.
     */
    @PostMapping("/{paypalOrderId}/authorize")
    public AuthorizeResponse authorize(@PathVariable String paypalOrderId, @Valid @RequestBody AuthorizeRequest req) {
        Product product = catalog.require(req.productId());

        PayPalOrderResponse res = paypal.authorizeOrder(paypalOrderId);
        String authId = res.firstAuthorizationId()
                .orElseThrow(() -> new IllegalStateException("PayPal returned no authorization for " + paypalOrderId));

        // Defensive: the amount PayPal authorized must match the catalog price.
        res.firstAmount().ifPresent(m -> {
            if (m.amount().compareTo(product.price()) != 0) {
                throw new IllegalStateException("Authorized amount %s does not match price %s"
                        .formatted(m.value(), product.price()));
            }
        });

        PurchaseOrder order = orders.save(new PurchaseOrder(product, paypalProps.currency(), paypalOrderId, authId));
        audit.record(order.getId(), Trigger.USER_CLICK, AuditService.ACTION_AUTHORIZED,
                "PayPal authorization " + authId, order.getAmount(), order.getCategory());

        // Layer 3: hard rules first. If one fires, no LLM is involved at all.
        Optional<Verdict> rule = guardrail.evaluate(order);
        if (rule.isPresent()) {
            Verdict v = verdicts.store(order, rule.get(), VerdictService.SOURCE_GUARDRAIL);
            audit.record(order.getId(), Trigger.USER_CLICK, AuditService.ACTION_GUARDRAIL,
                    v.action() + ": " + v.reasonToBuy(), order.getAmount(), order.getCategory());
            switch (v.action()) {
                case RELEASE -> {
                    order = stateMachine.capture(order, Trigger.USER_CLICK, "guardrail release: " + v.reasonToBuy());
                    trust.onClearRelease(order.getCategory());
                }
                case COOL -> order = stateMachine.startCooling(order, v.coolingHours(), Trigger.USER_CLICK, "guardrail");
                case BLOCK -> { /* guardrails never block outright; user decides on the verdict card */ }
            }
            return new AuthorizeResponse(OrderView.of(order, null), "VERDICT", null);
        }

        // Layer 2: open the interrogation.
        InterrogationService.Reply first = interrogation.open(order);
        order = orders.findById(order.getId()).orElseThrow();
        if (first.done()) {
            // interrogator unavailable -> go straight to verdict (which itself has a fail-safe)
            verdicts.decide(order);
            order = orders.findById(order.getId()).orElseThrow();
            return new AuthorizeResponse(OrderView.of(order, first.transcript()), "VERDICT", null);
        }
        return new AuthorizeResponse(OrderView.of(order, first.transcript()), "INTERROGATE", first.question());
    }

    public record CreateRequest(@NotBlank String productId) { }

    public record AuthorizeRequest(@NotBlank String productId) { }

    /** nextStep: INTERROGATE (chat with the agent) or VERDICT (show the card). */
    public record AuthorizeResponse(OrderView order, String nextStep, String question) { }
}
