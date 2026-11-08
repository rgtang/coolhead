package dev.you.coolhead.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.you.coolhead.audit.AuditLog.Trigger;

@Service
public class AuditService {

    public static final String ACTION_AUTHORIZED = "AUTHORIZED";
    public static final String ACTION_GUARDRAIL = "GUARDRAIL";
    public static final String ACTION_VERDICT = "VERDICT";
    public static final String ACTION_COOLING_STARTED = "COOLING_STARTED";
    public static final String ACTION_REMINDER = "REMINDER";
    public static final String ACTION_CAPTURED = "CAPTURED";
    public static final String ACTION_VOIDED = "VOIDED";
    public static final String ACTION_OVERRIDE = "OVERRIDE";

    private final AuditRepository repo;

    public AuditService(AuditRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public AuditLog record(String orderId, Trigger trigger, String action, String detail,
                           BigDecimal amount, String category) {
        return repo.save(new AuditLog(orderId, trigger, action, detail, amount, category));
    }

    /** Demo seeding only: record an entry with a historical timestamp. */
    @Transactional
    public AuditLog recordAt(String orderId, Trigger trigger, String action, String detail,
                             BigDecimal amount, String category, Instant at) {
        return repo.save(new AuditLog(orderId, trigger, action, detail, amount, category, at));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> recent() {
        return repo.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> forOrder(String orderId) {
        return repo.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional(readOnly = true)
    public long countSince(String action, Instant since) {
        return repo.countByActionAndCreatedAtAfter(action, since);
    }

    /** Headline numbers for the dashboard: "this month Coolhead stopped $X". */
    @Transactional(readOnly = true)
    public MonthlyStats monthlyStats() {
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);

        List<AuditLog> voided = repo.findByActionAndCreatedAtAfter(ACTION_VOIDED, monthStart);
        List<AuditLog> captured = repo.findByActionAndCreatedAtAfter(ACTION_CAPTURED, monthStart);

        BigDecimal blocked = voided.stream().map(a -> nz(a.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = captured.stream().map(a -> nz(a.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MonthlyStats(
                blocked,
                voided.size(),
                spent,
                captured.size(),
                repo.countByActionAndCreatedAtAfter(ACTION_COOLING_STARTED, monthStart),
                repo.countByActionAndCreatedAtAfter(ACTION_OVERRIDE, monthStart),
                voided.stream().filter(a -> a.getTrigger() == Trigger.TIMER).count());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record MonthlyStats(
            BigDecimal blockedAmount,
            int blockedCount,
            BigDecimal capturedAmount,
            int capturedCount,
            long coolingCount,
            long overrideCount,
            long timerVoidCount) {
    }
}
