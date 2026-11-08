package dev.you.coolhead.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.you.coolhead.config.LlmAvailability;
import dev.you.coolhead.config.PolicyProperties;
import dev.you.coolhead.order.OrderRepository;
import dev.you.coolhead.order.PurchaseOrder;

/**
 * Final review: facts + transcript -> LLM -> {@link Verdict}.
 * <p>
 * The verdict is stored on the order as advice. It does <em>not</em> change state; the user must click
 * "accept" / "override", which is what moves money (see AgentController + OrderStateMachine).
 */
@Service
public class VerdictService {

    private static final Logger log = LoggerFactory.getLogger(VerdictService.class);

    public static final String SOURCE_GUARDRAIL = "GUARDRAIL";
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_FALLBACK = "FALLBACK";

    static final String FALLBACK_NO_KEY = "no API key configured";

    private final ChatClient judge;
    private final FactCollector facts;
    private final InterrogationService interrogation;
    private final OrderRepository orders;
    private final PolicyProperties policy;
    private final LlmAvailability llm;

    public VerdictService(@Qualifier("judgeClient") ChatClient judge, FactCollector facts,
                          InterrogationService interrogation, OrderRepository orders, PolicyProperties policy,
                          LlmAvailability llm) {
        this.judge = judge;
        this.facts = facts;
        this.interrogation = interrogation;
        this.orders = orders;
        this.policy = policy;
        this.llm = llm;
    }

    @Transactional
    public Verdict decide(PurchaseOrder order) {
        if (!llm.available()) {
            Verdict verdict = Verdict.fallback(FALLBACK_NO_KEY);
            store(order, verdict, SOURCE_FALLBACK);
            return verdict;
        }

        String prompt = facts.collect(order).render() + "\n" + interrogation.renderTranscript(order)
                + "\nGive your verdict.";

        Verdict verdict;
        String source;
        try {
            verdict = judge.prompt().user(prompt).call().entity(Verdict.class);
            if (verdict == null || verdict.action() == null) throw new IllegalStateException("empty verdict");
            verdict = sanitize(verdict);
            source = SOURCE_LLM;
        } catch (Exception e) {
            log.warn("judge unavailable for order {}: {}", order.getId(), e.toString());
            verdict = Verdict.fallback(shortMessage(e));
            source = SOURCE_FALLBACK;
        }

        store(order, verdict, source);
        return verdict;
    }

    /** Persist a verdict produced elsewhere (guardrail). */
    @Transactional
    public Verdict store(PurchaseOrder order, Verdict verdict, String source) {
        order.recordVerdict(verdict.action().name(), verdict.coolingHours(), verdict.reasonToBuy(),
                verdict.reasonToWait(), source);
        orders.save(order);
        return verdict;
    }

    /** Clamp hours and make sure COOL always has a positive duration. */
    private Verdict sanitize(Verdict v) {
        int hours = v.action() == Verdict.Action.COOL
                ? policy.clampCoolingHours(v.coolingHours() <= 0 ? 24 : v.coolingHours())
                : 0;
        return new Verdict(v.action(), hours,
                v.reasonToBuy() == null ? "" : v.reasonToBuy().trim(),
                v.reasonToWait() == null ? "" : v.reasonToWait().trim());
    }

    private static final Pattern PROVIDER_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    /** Status line plus the provider's own message, without the raw JSON payload — this ends up on a user-facing card. */
    private static String shortMessage(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        Matcher provider = PROVIDER_MESSAGE.matcher(m);
        String detail = provider.find() ? provider.group(1) : null;
        int cut = m.indexOf('{');
        if (cut > 0) m = m.substring(0, cut);
        m = m.lines().findFirst().orElse(m).replaceAll("[\\s\\-:\\[]+$", "").trim();
        if (detail != null && !detail.isBlank()) m = m.isEmpty() ? detail : m + ": " + detail;
        if (m.isEmpty()) m = e.getClass().getSimpleName();
        return m.length() > 100 ? m.substring(0, 100) + "…" : m;
    }
}
