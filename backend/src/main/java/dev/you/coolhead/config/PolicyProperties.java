package dev.you.coolhead.config;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the deterministic layers (guardrails, cooling clamp, scheduler timing).
 * Everything here is enforced server-side regardless of what the LLM says.
 */
@ConfigurationProperties(prefix = "coolhead.policy")
public record PolicyProperties(
        Set<String> whitelistCategories,
        BigDecimal autoReleaseBelow,
        BigDecimal forceCoolAbove,
        int autoReleaseTrust,
        int minCoolingHours,
        int maxCoolingHours,
        int voidGraceHours,
        int maxInterrogationTurns) {

    public PolicyProperties {
        if (whitelistCategories == null) whitelistCategories = Set.of();
        if (autoReleaseBelow == null) autoReleaseBelow = new BigDecimal("20.00");
        if (forceCoolAbove == null) forceCoolAbove = new BigDecimal("1000.00");
        if (autoReleaseTrust <= 0) autoReleaseTrust = 85;
        if (minCoolingHours <= 0) minCoolingHours = 1;
        if (maxCoolingHours <= 0) maxCoolingHours = 72;
        if (voidGraceHours <= 0) voidGraceHours = 24;
        if (maxInterrogationTurns <= 0) maxInterrogationTurns = 3;
    }

    /** Clamp an LLM-proposed cooling duration into the allowed window. */
    public int clampCoolingHours(int requested) {
        return Math.max(minCoolingHours, Math.min(maxCoolingHours, requested));
    }
}
