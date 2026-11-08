package dev.you.coolhead.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One authorized purchase travelling through the cooling pipeline.
 * <p>
 * The {@code state} field is package-private on purpose: only {@link OrderStateMachine} (same package) may mutate it.
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Version
    private long version;

    // ---- what was bought
    @Column(nullable = false) private String productId;
    @Column(nullable = false) private String productName;
    @Column(nullable = false, length = 32) private String category;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;

    // ---- PayPal handles
    @Column(nullable = false) private String paypalOrderId;
    @Column(nullable = false) private String paypalAuthorizationId;
    private String paypalCaptureId;

    // ---- state machine
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderState state = OrderState.AUTHORIZED;

    private Integer coolingHours;
    private Instant coolingUntil;
    private Instant remindedAt;

    // ---- agent output (advice only; never drives a transition by itself)
    @Column(length = 16) private String verdictAction;
    private Integer verdictCoolingHours;              // proposed; the clamped value lands in coolingHours on accept
    @Column(length = 500) private String reasonToBuy;
    @Column(length = 500) private String reasonToWait;
    @Column(length = 64) private String verdictSource;   // GUARDRAIL / LLM / FALLBACK
    @Column(length = 500) private String overrideReason;
    private int interrogationTurns;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String transcriptJson;

    // ---- timestamps
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(Product product, String currency, String paypalOrderId, String paypalAuthorizationId) {
        this.productId = product.id();
        this.productName = product.name();
        this.category = product.category();
        this.amount = product.price();
        this.currency = currency;
        this.paypalOrderId = paypalOrderId;
        this.paypalAuthorizationId = paypalAuthorizationId;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    // ---- package-private mutators used by OrderStateMachine only
    void setState(OrderState state) { this.state = state; }
    void setCooling(int hours, Instant until) { this.coolingHours = hours; this.coolingUntil = until; }
    void setRemindedAt(Instant t) { this.remindedAt = t; }
    void setPaypalCaptureId(String id) { this.paypalCaptureId = id; }
    void setOverrideReason(String reason) { this.overrideReason = reason; }

    /** Demo seeding only: place a historical order in the past. */
    void setTimestamps(Instant created, Instant updated) { this.createdAt = created; this.updatedAt = updated; }

    // ---- public mutators for agent data (not state)
    public void recordVerdict(String action, int coolingHours, String reasonToBuy, String reasonToWait, String source) {
        this.verdictAction = action;
        this.verdictCoolingHours = coolingHours;
        this.reasonToBuy = reasonToBuy;
        this.reasonToWait = reasonToWait;
        this.verdictSource = source;
    }

    public void recordTranscript(String json, int turns) {
        this.transcriptJson = json;
        this.interrogationTurns = turns;
    }

    public boolean coolingElapsed() {
        return state == OrderState.COOLING && coolingUntil != null && Instant.now().isAfter(coolingUntil);
    }

    // ---- getters
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaypalOrderId() { return paypalOrderId; }
    public String getPaypalAuthorizationId() { return paypalAuthorizationId; }
    public String getPaypalCaptureId() { return paypalCaptureId; }
    public OrderState getState() { return state; }
    public Integer getCoolingHours() { return coolingHours; }
    public Instant getCoolingUntil() { return coolingUntil; }
    public Instant getRemindedAt() { return remindedAt; }
    public String getVerdictAction() { return verdictAction; }
    public Integer getVerdictCoolingHours() { return verdictCoolingHours; }
    public String getReasonToBuy() { return reasonToBuy; }
    public String getReasonToWait() { return reasonToWait; }
    public String getVerdictSource() { return verdictSource; }
    public String getOverrideReason() { return overrideReason; }
    public int getInterrogationTurns() { return interrogationTurns; }
    public String getTranscriptJson() { return transcriptJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
