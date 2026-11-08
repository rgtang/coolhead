package dev.you.coolhead.paypal.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /v2/checkout/orders body. We always use intent=AUTHORIZE so money is held, not moved. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateOrderRequest(
        @JsonProperty("intent") String intent,
        @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits) {

    public static CreateOrderRequest authorize(String referenceId, String description, Money amount) {
        return new CreateOrderRequest("AUTHORIZE", List.of(new PurchaseUnit(referenceId, description, amount)));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PurchaseUnit(
            @JsonProperty("reference_id") String referenceId,
            @JsonProperty("description") String description,
            @JsonProperty("amount") Money amount) {
    }
}
