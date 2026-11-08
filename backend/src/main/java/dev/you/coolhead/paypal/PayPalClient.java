package dev.you.coolhead.paypal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import dev.you.coolhead.config.PayPalProperties;
import dev.you.coolhead.paypal.dto.AccessTokenResponse;
import dev.you.coolhead.paypal.dto.CaptureResponse;
import dev.you.coolhead.paypal.dto.CreateOrderRequest;
import dev.you.coolhead.paypal.dto.Money;
import dev.you.coolhead.paypal.dto.PayPalOrderResponse;

/**
 * Thin PayPal Orders v2 / Payments v2 client.
 * <p>
 * Flow: {@link #createOrder} (intent=AUTHORIZE) -> buyer approves in the PayPal popup ->
 * {@link #authorizeOrder} (holds funds) -> later {@link #captureAuthorization} or {@link #voidAuthorization}.
 * An authorization is honored for 3 days and valid for up to 29 days, which comfortably covers a 72h cool-down.
 * <p>
 * When no credentials are configured ({@link PayPalProperties#mock()}), every method returns realistic fakes so
 * the whole flow can be demoed offline.
 */
@Component
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);

    private final PayPalProperties props;
    private final RestClient http;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalClient(PayPalProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrl()).build();
        if (props.mock()) {
            log.warn("PayPal credentials missing -> running PayPalClient in MOCK mode");
        }
    }

    public boolean isMock() {
        return props.mock();
    }

    // ------------------------------------------------------------------ OAuth

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        AccessTokenResponse res = http.post()
                .uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(props.clientId(), props.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AccessTokenResponse.class);

        if (res == null || res.accessToken() == null) {
            throw new IllegalStateException("PayPal OAuth returned no token");
        }
        cachedToken = res.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(res.expiresIn());
        log.debug("Refreshed PayPal access token, valid until {}", tokenExpiresAt);
        return cachedToken;
    }

    // ------------------------------------------------------------------ Orders

    /** Create an order with intent=AUTHORIZE. Returns the PayPal order id the frontend hands to the PayPal button. */
    public PayPalOrderResponse createOrder(String referenceId, String description, BigDecimal amount) {
        Money money = Money.of(props.currency(), amount);
        if (props.mock()) {
            return new PayPalOrderResponse("MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    "CREATED",
                    List.of(new PayPalOrderResponse.PurchaseUnit(referenceId, money, null)),
                    List.of());
        }
        return http.post()
                .uri("/v2/checkout/orders")
                .headers(h -> h.setBearerAuth(accessToken()))
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(CreateOrderRequest.authorize(referenceId, description, money))
                .retrieve()
                .body(PayPalOrderResponse.class);
    }

    /** Authorize an approved order: funds are held, nothing is charged yet. */
    public PayPalOrderResponse authorizeOrder(String paypalOrderId) {
        if (props.mock()) {
            var auth = new PayPalOrderResponse.Authorization(
                    "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    "CREATED", null, Instant.now().plusSeconds(29L * 24 * 3600).toString());
            return new PayPalOrderResponse(paypalOrderId, "COMPLETED",
                    List.of(new PayPalOrderResponse.PurchaseUnit(null, null,
                            new PayPalOrderResponse.Payments(List.of(auth)))),
                    List.of());
        }
        return http.post()
                .uri("/v2/checkout/orders/{id}/authorize", paypalOrderId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of())
                .retrieve()
                .body(PayPalOrderResponse.class);
    }

    // ------------------------------------------------------------------ Authorizations

    /** Capture (actually charge) a held authorization. Only ever called from OrderStateMachine. */
    public CaptureResponse captureAuthorization(String authorizationId) {
        if (props.mock()) {
            return new CaptureResponse("CAP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    "COMPLETED", null);
        }
        return http.post()
                .uri("/v2/payments/authorizations/{id}/capture", authorizationId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("final_capture", true))
                .retrieve()
                .body(CaptureResponse.class);
    }

    /** Void (release) a held authorization. Only ever called from OrderStateMachine. */
    public void voidAuthorization(String authorizationId) {
        if (props.mock()) {
            log.info("[mock] void authorization {}", authorizationId);
            return;
        }
        http.post()
                .uri("/v2/payments/authorizations/{id}/void", authorizationId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .header("PayPal-Request-Id", UUID.randomUUID().toString())
                .retrieve()
                .toBodilessEntity();
    }
}
