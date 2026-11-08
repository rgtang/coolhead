package dev.you.coolhead.agent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output of the final review. Always carries <em>both</em> sides so the user sees a balanced card,
 * not a lecture. The LLM proposes {@code coolingHours}; OrderStateMachine clamps it.
 */
@JsonClassDescription("Final advice on an authorized purchase. The user decides; this is a recommendation.")
public record Verdict(
        @JsonPropertyDescription("RELEASE = pay now, COOL = hold for coolingHours then ask again, BLOCK = recommend releasing the hold")
        Action action,
        @JsonPropertyDescription("Hours to hold the payment when action is COOL (1-72). 0 otherwise.")
        int coolingHours,
        @JsonPropertyDescription("Strongest honest reason the user should go ahead. Under 40 words, second person.")
        String reasonToBuy,
        @JsonPropertyDescription("Strongest honest reason the user should wait. Under 40 words, second person.")
        String reasonToWait) {

    public enum Action { RELEASE, COOL, BLOCK }

    public static Verdict release(String reasonToBuy, String reasonToWait) {
        return new Verdict(Action.RELEASE, 0, reasonToBuy, reasonToWait);
    }

    public static Verdict cool(int hours, String reasonToBuy, String reasonToWait) {
        return new Verdict(Action.COOL, hours, reasonToBuy, reasonToWait);
    }

    public static Verdict block(String reasonToBuy, String reasonToWait) {
        return new Verdict(Action.BLOCK, 0, reasonToBuy, reasonToWait);
    }

    /** Fail-safe used when the LLM is unavailable or returns garbage: hold for a day, say so plainly. */
    public static Verdict fallback(String why) {
        return cool(24,
                "You may well need this — the reviewer could not evaluate it right now.",
                "The AI reviewer was unavailable (" + why + "), so the default 24h pause applies. You can override with a reason.");
    }
}
