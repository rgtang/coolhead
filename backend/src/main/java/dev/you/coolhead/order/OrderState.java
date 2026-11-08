package dev.you.coolhead.order;

/**
 * Lifecycle of a purchase after the buyer approved the PayPal authorization.
 *
 * <pre>
 *  AUTHORIZED ──(guardrail/verdict RELEASE, override, USER_CLICK)──▶ CAPTURED
 *      │
 *      ├──(verdict COOL, USER_CLICK)──▶ COOLING ──(confirm/override, USER_CLICK)──▶ CAPTURED
 *      │                                   │
 *      │                                   └──(cancel USER_CLICK | timeout TIMER)──▶ VOIDED
 *      │
 *      └──(verdict BLOCK accepted / cancel, USER_CLICK)──▶ VOIDED
 * </pre>
 */
public enum OrderState {
    AUTHORIZED,
    COOLING,
    CAPTURED,
    VOIDED;

    public boolean isTerminal() {
        return this == CAPTURED || this == VOIDED;
    }
}
