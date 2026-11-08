package dev.you.coolhead.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /v2/payments/authorizations/{id}/capture response (subset). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptureResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("amount") Money amount) {
}
