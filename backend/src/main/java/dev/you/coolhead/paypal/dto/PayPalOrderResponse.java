package dev.you.coolhead.paypal.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared shape for POST /v2/checkout/orders and POST /v2/checkout/orders/{id}/authorize responses.
 * Only the fields we read are modelled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalOrderResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits,
        @JsonProperty("links") List<Link> links) {

    /** First authorization id, present after /authorize succeeds. */
    public Optional<String> firstAuthorizationId() {
        if (purchaseUnits == null) return Optional.empty();
        return purchaseUnits.stream()
                .filter(pu -> pu.payments() != null && pu.payments().authorizations() != null)
                .flatMap(pu -> pu.payments().authorizations().stream())
                .map(Authorization::id)
                .findFirst();
    }

    public Optional<Money> firstAmount() {
        if (purchaseUnits == null || purchaseUnits.isEmpty()) return Optional.empty();
        PurchaseUnit pu = purchaseUnits.get(0);
        if (pu.amount() != null) return Optional.of(pu.amount());
        return firstAuthorizationId().isPresent()
                ? Optional.ofNullable(pu.payments().authorizations().get(0).amount())
                : Optional.empty();
    }

    public Optional<String> approveLink() {
        if (links == null) return Optional.empty();
        return links.stream().filter(l -> "approve".equals(l.rel())).map(Link::href).findFirst();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PurchaseUnit(
            @JsonProperty("reference_id") String referenceId,
            @JsonProperty("amount") Money amount,
            @JsonProperty("payments") Payments payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payments(@JsonProperty("authorizations") List<Authorization> authorizations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Authorization(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("amount") Money amount,
            @JsonProperty("expiration_time") String expirationTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(
            @JsonProperty("href") String href,
            @JsonProperty("rel") String rel,
            @JsonProperty("method") String method) {
    }
}
