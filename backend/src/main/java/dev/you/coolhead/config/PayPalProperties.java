package dev.you.coolhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code coolhead.paypal.*}, which in turn reads PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET /
 * PAYPAL_BASE_URL from the environment (see application.yml and .env.example).
 */
@ConfigurationProperties(prefix = "coolhead.paypal")
public record PayPalProperties(
        String clientId,
        String clientSecret,
        String baseUrl,
        String currency) {

    public PayPalProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api-m.sandbox.paypal.com";
        if (currency == null || currency.isBlank()) currency = "USD";
    }

    /** No credentials configured -> PayPalClient runs in mock mode (no network). */
    public boolean mock() {
        return clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank();
    }
}
