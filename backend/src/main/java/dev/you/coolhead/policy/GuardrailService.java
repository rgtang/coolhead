package dev.you.coolhead.policy;

import java.util.Optional;

import org.springframework.stereotype.Service;

import dev.you.coolhead.agent.Verdict;
import dev.you.coolhead.config.PolicyProperties;
import dev.you.coolhead.order.PurchaseOrder;

/**
 * Layer 3 — deterministic rules evaluated <em>before</em> any LLM call.
 * <p>
 * If a rule fires, the returned Verdict is final for this order and the interrogation is skipped entirely.
 * Rules are intentionally boring: whitelist, small-amount floor, large-amount ceiling, high-trust category.
 */
@Service
public class GuardrailService {

    private final PolicyProperties policy;
    private final TrustScoreService trust;

    public GuardrailService(PolicyProperties policy, TrustScoreService trust) {
        this.policy = policy;
        this.trust = trust;
    }

    public Optional<Verdict> evaluate(PurchaseOrder order) {
        String category = order.getCategory() == null ? "" : order.getCategory().toLowerCase();

        if (policy.whitelistCategories().contains(category)) {
            return Optional.of(Verdict.release(
                    "'%s' is an essential category on your whitelist.".formatted(category),
                    "None — essentials are not subject to cooling."));
        }

        if (order.getAmount().compareTo(policy.autoReleaseBelow()) < 0) {
            return Optional.of(Verdict.release(
                    "Under $%s — too small to be worth a debate.".formatted(policy.autoReleaseBelow().toPlainString()),
                    "None — small purchases are released automatically."));
        }

        int score = trust.get(category);
        if (score >= policy.autoReleaseTrust()) {
            return Optional.of(Verdict.release(
                    "Your track record in '%s' is excellent (trust %d/100).".formatted(category, score),
                    "None — you've earned the benefit of the doubt here."));
        }

        if (order.getAmount().compareTo(policy.forceCoolAbove()) > 0) {
            return Optional.of(Verdict.cool(policy.maxCoolingHours(),
                    "Big purchases can be genuinely worth it when planned.",
                    "Anything over $%s always gets the full %dh pause — no exceptions, no arguing with a chatbot."
                            .formatted(policy.forceCoolAbove().toPlainString(), policy.maxCoolingHours())));
        }

        return Optional.empty(); // hand over to Layer 2 (interrogation) + verdict
    }
}
