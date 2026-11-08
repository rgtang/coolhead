package dev.you.coolhead.paypal.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PayPal money object: {"currency_code":"USD","value":"12.34"}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Money(
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("value") String value) {

    public static Money of(String currency, BigDecimal amount) {
        return new Money(currency, amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    public BigDecimal amount() {
        return new BigDecimal(value);
    }
}
