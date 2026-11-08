package dev.you.coolhead.order;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.you.coolhead.agent.InterrogationService.Turn;
import dev.you.coolhead.audit.AuditLog.Trigger;
import dev.you.coolhead.audit.AuditService;
import dev.you.coolhead.config.PayPalProperties;
import dev.you.coolhead.policy.TrustScoreService;

/**
 * Pre-populates a few past orders, their audit trail and starting trust scores so the dashboard tells a story on
 * first launch. Enabled with {@code COOLHEAD_SEED=true}; skipped if the database already has orders.
 * <p>
 * Lives in this package because it writes historical state directly instead of replaying it through
 * {@link OrderStateMachine}, which would call PayPal. Every seeded order is terminal (CAPTURED or VOIDED), so the
 * fake PayPal ids are never used for a capture or void.
 */
@Component
@ConditionalOnProperty(name = "coolhead.seed.enabled", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final Map<String, Integer> TRUST = Map.of(
            "gaming", 40,
            "electronics", 60,
            "fashion", 58,
            "groceries", 52);

    private final OrderRepository orders;
    private final ProductCatalog catalog;
    private final AuditService audit;
    private final TrustScoreService trust;
    private final PayPalProperties paypal;
    private final ObjectMapper mapper;

    public DemoSeeder(OrderRepository orders, ProductCatalog catalog, AuditService audit, TrustScoreService trust,
                      PayPalProperties paypal, ObjectMapper mapper) {
        this.orders = orders;
        this.catalog = catalog;
        this.audit = audit;
        this.trust = trust;
        this.paypal = paypal;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (orders.count() > 0) {
            log.info("Demo seed skipped: database already has orders");
            return;
        }
        seedTimerVoid();
        seedOverride();
        seedCancelAfterCooling();
        seedGuardrailRelease();
        TRUST.forEach(trust::set);
        log.info("Demo seed: 4 historical orders, trust {}", TRUST);
    }

    /** Headphones: cooled 24h, reminded, nobody came back -> TIMER voids. */
    private void seedTimerVoid() {
        Instant t0 = at(5, 21, 40);
        PurchaseOrder o = newOrder("p-headphones", "001");
        transcript(o,
                "Coolhead: You already own two pairs of ANC headphones. What's different about these?",
                "User: The reviews say the noise cancelling is way better.",
                "Coolhead: Is something wrong with the ones you have, or is it more about the upgrade?",
                "User: Honestly they work fine, I just saw a video about these.");
        o.recordVerdict("COOL", 24,
                "If you commute daily in noise, better ANC is a real quality-of-life gain.",
                "Your current pairs work fine and this started with a review video — a classic upgrade itch.",
                "LLM");
        Instant coolStart = t0.plus(Duration.ofMinutes(4));
        Instant coolUntil = coolStart.plus(Duration.ofHours(24));
        Instant reminded = coolUntil.plus(Duration.ofMinutes(1));
        Instant voided = reminded.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(1));
        o.setCooling(24, coolUntil);
        o.setRemindedAt(reminded);
        o.setState(OrderState.VOIDED);
        save(o, t0, voided);

        authorized(o, t0);
        verdict(o, t0.plus(Duration.ofMinutes(3)));
        log(o, Trigger.USER_CLICK, AuditService.ACTION_COOLING_STARTED,
                "cooling 24h (requested 24h) until %s — accepted COOL verdict".formatted(coolUntil), coolStart);
        log(o, Trigger.TIMER, AuditService.ACTION_REMINDER,
                "cooling ended; waiting 24h for a decision before auto-void", reminded);
        log(o, Trigger.TIMER, AuditService.ACTION_VOIDED, "no decision within 24h after cooling ended", voided);
    }

    /** Console at 1 a.m.: verdict said cool 48h, user overrode with a reason -> CAPTURED. */
    private void seedOverride() {
        Instant t0 = at(4, 1, 12);
        PurchaseOrder o = newOrder("p-console", "002");
        transcript(o,
                "Coolhead: It's past 1 a.m. — what made tonight the night to buy a console?",
                "User: The new trailer dropped and my friends are all playing it this weekend.",
                "Coolhead: Would buying it on Friday morning change anything for the weekend plans?",
                "User: Probably not, but I want to set it up before then.");
        o.recordVerdict("COOL", 48,
                "Playing with friends this weekend is a real social reason, not just hype.",
                "It's 1 a.m. right after a trailer, and Friday morning would still get it to you in time.",
                "LLM");
        String reason = "Friends' game night is Saturday and shipping takes three days";
        o.setOverrideReason(reason);
        o.setPaypalCaptureId("SEED-CAP-002");
        o.setState(OrderState.CAPTURED);
        Instant overridden = t0.plus(Duration.ofMinutes(5));
        save(o, t0, overridden);

        authorized(o, t0);
        verdict(o, t0.plus(Duration.ofMinutes(3)));
        log(o, Trigger.USER_CLICK, AuditService.ACTION_OVERRIDE, reason, overridden);
        log(o, Trigger.USER_CLICK, AuditService.ACTION_CAPTURED, "override: " + reason,
                overridden.plusSeconds(1));
    }

    /** Sneakers: cooled 24h, after the reminder the user cancelled -> VOIDED by USER_CLICK. */
    private void seedCancelAfterCooling() {
        Instant t0 = at(3, 20, 5);
        PurchaseOrder o = newOrder("p-sneakers", "003");
        transcript(o,
                "Coolhead: The countdown says 300 pairs left. Would you want these if there were 3,000?",
                "User: Maybe not as badly, they just look rare.",
                "Coolhead: Where do you picture wearing them in the next month?",
                "User: Not sure yet, probably just keep them clean.");
        o.recordVerdict("COOL", 24,
                "If you collect sneakers, limited drops are part of the fun and can hold value.",
                "The pull is the scarcity timer, and you have no concrete plan to wear them.",
                "LLM");
        Instant coolStart = t0.plus(Duration.ofMinutes(3));
        Instant coolUntil = coolStart.plus(Duration.ofHours(24));
        Instant reminded = coolUntil.plus(Duration.ofMinutes(1));
        Instant cancelled = reminded.plus(Duration.ofHours(2));
        o.setCooling(24, coolUntil);
        o.setRemindedAt(reminded);
        o.setState(OrderState.VOIDED);
        save(o, t0, cancelled);

        authorized(o, t0);
        verdict(o, t0.plus(Duration.ofMinutes(2)));
        log(o, Trigger.USER_CLICK, AuditService.ACTION_COOLING_STARTED,
                "cooling 24h (requested 24h) until %s — accepted COOL verdict".formatted(coolUntil), coolStart);
        log(o, Trigger.TIMER, AuditService.ACTION_REMINDER,
                "cooling ended; waiting 24h for a decision before auto-void", reminded);
        log(o, Trigger.USER_CLICK, AuditService.ACTION_VOIDED, "cancelled during/after cooling", cancelled);
    }

    /** Groceries: whitelisted, released by a hard rule with no AI involved. */
    private void seedGuardrailRelease() {
        Instant t0 = at(2, 10, 30);
        PurchaseOrder o = newOrder("p-groceries", "004");
        String why = "'groceries' is an essential category on your whitelist.";
        o.recordVerdict("RELEASE", 0, why, "None — essentials are not subject to cooling.", "GUARDRAIL");
        o.setPaypalCaptureId("SEED-CAP-004");
        o.setState(OrderState.CAPTURED);
        save(o, t0, t0.plusSeconds(2));

        authorized(o, t0);
        log(o, Trigger.USER_CLICK, AuditService.ACTION_GUARDRAIL, "RELEASE: " + why, t0.plusSeconds(1));
        log(o, Trigger.USER_CLICK, AuditService.ACTION_CAPTURED, "guardrail release: " + why, t0.plusSeconds(2));
    }

    // ------------------------------------------------------------------------------------------------

    private PurchaseOrder newOrder(String productId, String suffix) {
        return new PurchaseOrder(catalog.require(productId), paypal.currency(),
                "SEED-ORDER-" + suffix, "SEED-AUTH-" + suffix);
    }

    private void save(PurchaseOrder o, Instant created, Instant updated) {
        o.setTimestamps(created, updated);
        orders.save(o);
    }

    private void authorized(PurchaseOrder o, Instant at) {
        log(o, Trigger.USER_CLICK, AuditService.ACTION_AUTHORIZED,
                "PayPal authorization " + o.getPaypalAuthorizationId(), at);
    }

    private void verdict(PurchaseOrder o, Instant at) {
        log(o, Trigger.USER_CLICK, AuditService.ACTION_VERDICT,
                "%s %dh — buy: %s | wait: %s".formatted(o.getVerdictAction(), o.getVerdictCoolingHours(),
                        o.getReasonToBuy(), o.getReasonToWait()), at);
    }

    private void log(PurchaseOrder o, Trigger trigger, String action, String detail, Instant at) {
        audit.recordAt(o.getId(), trigger, action, detail, o.getAmount(), o.getCategory(), at);
    }

    /** Lines prefixed "Coolhead: " become assistant turns, "User: " user turns. */
    private void transcript(PurchaseOrder o, String... lines) {
        List<Turn> turns = Arrays.stream(lines)
                .map(l -> l.startsWith("User: ")
                        ? new Turn("user", l.substring("User: ".length()))
                        : new Turn("assistant", l.substring("Coolhead: ".length())))
                .toList();
        try {
            o.recordTranscript(mapper.writeValueAsString(turns),
                    (int) turns.stream().filter(t -> "user".equals(t.role())).count());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize demo transcript", e);
        }
    }

    /** A wall-clock time {@code daysAgo} days back, in the same zone FactCollector uses. */
    private static Instant at(int daysAgo, int hour, int minute) {
        return LocalDate.now(ZONE).minusDays(daysAgo).atTime(hour, minute).atZone(ZONE).toInstant();
    }
}
