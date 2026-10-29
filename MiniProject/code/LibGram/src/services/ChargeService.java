package services;

import data.ChargeRepository;
import data.LoanRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import model.Charge;
import model.ChargeStatus;
import model.ChargeType;
import model.User;

/** Owns the dues ledger and its balance/status arithmetic. */
public final class ChargeService implements DuesProvider {
    private final ChargeRepository repository;
    private final PermissionPolicy permissions;
    private final BillingEventListener events;

    public ChargeService(ChargeRepository repository) {
        this(repository, new PermissionPolicy(), BillingEventListener.NO_OP);
    }

    public ChargeService(ChargeRepository repository, PermissionPolicy permissions) {
        this(repository, permissions, BillingEventListener.NO_OP);
    }

    public ChargeService(ChargeRepository repository, PermissionPolicy permissions, BillingEventListener events) {
        this.repository = repository;
        this.permissions = permissions;
        this.events = events;
    }

    public List<Charge> forMember(String memberId) {
        return repository.findAll().stream().filter(c -> c.memberId().equals(memberId))
                .sorted(Comparator.comparing(Charge::createdAt).thenComparing(Charge::chargeId)).toList();
    }

    public BigDecimal unpaidBalance(String memberId) {
        return forMember(memberId).stream().map(Charge::balance).filter(b -> b.signum() > 0)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add).setScale(2);
    }

    public BigDecimal refundDue(String memberId) {
        return forMember(memberId).stream().filter(c -> c.type() == ChargeType.REFUND)
                .map(Charge::balance).filter(balance -> balance.signum() < 0).map(BigDecimal::negate)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add).setScale(2);
    }

    Charge create(String memberId, String loanId, ChargeType type, String description, BigDecimal amount) {
        BigDecimal normalizedAmount = ChargeCalculator.money(amount);
        return new Charge(UUID.randomUUID().toString(), memberId, loanId == null ? "" : loanId, type, description,
                normalizedAmount, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), ChargeStatus.UNPAID,
                LocalDateTime.now());
    }

    public Charge add(String memberId, String loanId, ChargeType type, String description, BigDecimal amount)
            throws IOException {
        Charge charge = create(memberId, loanId, type, description, amount);
        List<Charge> records = new java.util.ArrayList<>(repository.findAll());
        records.add(charge);
        repository.saveAll(records);
        return charge;
    }

    public Charge addManual(User actor, String memberId, ChargeType type, String description, BigDecimal amount)
            throws IOException {
        requireAdmin(actor);
        if (!List.of(ChargeType.MEMBERSHIP_REGISTRATION, ChargeType.MEMBERSHIP_RENEWAL, ChargeType.CARD_REPLACEMENT)
                .contains(type))
            throw new IllegalArgumentException("Manual charges are limited to membership and card fees.");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Manual charge must be greater than zero.");
        return add(memberId, "", type, description, amount);
    }

    public List<Charge> createAll(String memberId, String loanId, List<ChargeCalculator.Line> lines) {
        return lines.stream().filter(line -> line.amount().signum() != 0)
                .map(line -> create(memberId, loanId, line.type(), line.description(), line.amount())).toList();
    }

    public void addAll(String memberId, String loanId, List<ChargeCalculator.Line> lines) throws IOException {
        appendAll(createAll(memberId, loanId, lines));
    }

    void appendAll(List<Charge> additions) throws IOException {
        if (additions.isEmpty())
            return;
        List<Charge> updated = new java.util.ArrayList<>(repository.findAll());
        updated.addAll(additions);
        repository.saveAll(updated);
    }

    List<Charge> allRecords() {
        return repository.findAll();
    }

    void replaceAll(List<Charge> records) throws IOException {
        repository.saveAll(records);
    }

    public int migrateActiveLoanBases(LoanRepository loans) throws IOException {
        int created = 0;
        List<Charge> additions = new java.util.ArrayList<>();
        List<Charge> existingCharges = repository.findAll();
        for (var loan : loans.findAll())
            if (loan.status() == model.LoanStatus.ACTIVE && loan.baseCharge().signum() > 0
                    && existingCharges.stream()
                            .noneMatch(c -> c.loanId().equals(loan.loanId()) && c.type() == ChargeType.BASE)) {
                additions
                        .add(create(loan.memberId(), loan.loanId(), ChargeType.BASE, "Base charge", loan.baseCharge()));
                created++;
            }
        appendAll(additions);
        return created;
    }

    public Charge waive(User actor, String chargeId, BigDecimal amount, String reason) throws IOException {
        requireAdmin(actor);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A waiver reason is required.");
        var records = new java.util.ArrayList<>(repository.findAll());
        int index = indexOf(records, chargeId);
        Charge old = records.get(index);
        if (old.amount().signum() < 0)
            throw new IllegalArgumentException("Refund credits cannot be waived.");
        BigDecimal normalized = ChargeCalculator.money(amount);
        if (normalized.signum() <= 0 || normalized.compareTo(old.balance()) > 0)
            throw new IllegalArgumentException("Waiver must be positive and cannot exceed the outstanding balance.");
        BigDecimal next = old.waivedAmount().add(normalized);
        Charge updated = new Charge(old.chargeId(), old.memberId(), old.loanId(), old.type(),
                old.description() + " | Waiver: " + reason.trim(), old.amount(), next, old.paidAmount(),
                ChargeStatus.UNPAID, old.createdAt()).withAmounts(next, old.paidAmount());
        records.set(index, updated);
        repository.saveAll(records);
        events.onFineWaived(updated);
        return updated;
    }

    Charge waiveInternal(Charge charge, BigDecimal amount, String reason) throws IOException {
        BigDecimal next = charge.waivedAmount().add(ChargeCalculator.money(amount));
        Charge updated = new Charge(charge.chargeId(), charge.memberId(), charge.loanId(), charge.type(),
                charge.description() + " | " + reason, charge.amount(), charge.waivedAmount(), charge.paidAmount(),
                charge.status(), charge.createdAt()).withAmounts(next, charge.paidAmount());
        return update(updated);
    }

    public Charge update(Charge updated) throws IOException {
        var records = new java.util.ArrayList<>(repository.findAll());
        int i = indexOf(records, updated.chargeId());
        records.set(i, updated);
        repository.saveAll(records);
        return updated;
    }

    public void requireAdmin(User actor) {
        if (actor == null || !actor.active() || !permissions.canManageMembers(actor))
            throw new SecurityException("Only an active administrator can perform this billing action.");
    }

    private static int indexOf(List<Charge> records, String id) {
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).chargeId().equals(id))
                return i;
        throw new IllegalArgumentException("Charge does not exist.");
    }
}
