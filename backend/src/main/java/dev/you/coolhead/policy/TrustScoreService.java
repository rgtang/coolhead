package dev.you.coolhead.policy;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Layer 4 — per-category trust score (0..100, default 50).
 * <p>
 * The score answers "how often does this person's judgement in this category turn out fine?". It is fed to the
 * LLM as a fact and, once high enough, lets a category bypass interrogation entirely (see GuardrailService).
 * <p>
 * Kept in memory for the demo; swap for a JPA entity if persistence across restarts matters.
 */
@Service
public class TrustScoreService {

    private static final Logger log = LoggerFactory.getLogger(TrustScoreService.class);

    public static final int DEFAULT = 50;
    public static final int MIN = 0;
    public static final int MAX = 100;

    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public int get(String category) {
        return scores.getOrDefault(key(category), DEFAULT);
    }

    public Map<String, Integer> snapshot() {
        return new TreeMap<>(scores);
    }

    // ---- events ---------------------------------------------------------------

    /** Guardrail or verdict said RELEASE and the user went ahead: mild positive signal. */
    public void onClearRelease(String category) { adjust(category, +2, "clean release"); }

    /** User waited out the whole cooling window and then still bought: deliberate decision. */
    public void onConfirmAfterCooling(String category) { adjust(category, +5, "confirmed after cooling"); }

    /** User cancelled during/after cooling: the system did its job. */
    public void onCancelAfterCooling(String category) { adjust(category, +8, "cancelled after cooling"); }

    /** Timer voided an abandoned order: user forgot about it — classic impulse. */
    public void onTimerVoid(String category) { adjust(category, +3, "forgotten, voided by timer"); }

    /** User overrode a COOL/BLOCK verdict: strong negative signal. */
    public void onOverride(String category) { adjust(category, -10, "override"); }

    /** User accepted a BLOCK verdict: strong positive signal. */
    public void onAcceptBlock(String category) { adjust(category, +10, "accepted block"); }

    // ---------------------------------------------------------------------------

    public int adjust(String category, int delta, String reason) {
        int next = scores.compute(key(category), (k, v) -> clamp((v == null ? DEFAULT : v) + delta));
        log.debug("trust[{}] {}{} -> {} ({})", category, delta >= 0 ? "+" : "", delta, next, reason);
        return next;
    }

    private static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    private static String key(String category) {
        return category == null ? "unknown" : category.toLowerCase();
    }
}
