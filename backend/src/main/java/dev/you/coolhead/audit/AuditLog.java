package dev.you.coolhead.audit;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only record of every state transition.
 * <p>
 * {@link Trigger} deliberately has no LLM value: the model can only <em>recommend</em>; every capture/void/cooling
 * transition is caused by a human click or the timer. This is the auditable guarantee of the system.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    public enum Trigger { TIMER, USER_CLICK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Trigger trigger;

    /** e.g. AUTHORIZED, GUARDRAIL_RELEASE, VERDICT, COOLING_STARTED, REMINDER, CAPTURED, VOIDED, OVERRIDE. */
    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 32)
    private String category;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(String orderId, Trigger trigger, String action, String detail, BigDecimal amount, String category) {
        this.orderId = orderId;
        this.trigger = trigger;
        this.action = action;
        this.detail = detail;
        this.amount = amount;
        this.category = category;
    }

    /** Demo seeding only: an entry that happened in the past. */
    public AuditLog(String orderId, Trigger trigger, String action, String detail, BigDecimal amount, String category,
                    Instant createdAt) {
        this(orderId, trigger, action, detail, amount, category);
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public Trigger getTrigger() { return trigger; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}
