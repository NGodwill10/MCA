package services;

import data.PaymentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import model.ChargeType;
import model.Charge;
import model.Payment;
import model.PaymentMethod;
import model.User;

public final class PaymentService {
    public record Receipt(String receiptNumber, List<Payment> payments) {
        public Receipt {
            payments = List.copyOf(payments);
        }
    }

    private final ChargeService charges;
    private final PaymentRepository payments;
    private final BillingEventListener events;

    public PaymentService(ChargeService charges, PaymentRepository payments) {
        this(charges, payments, BillingEventListener.NO_OP);
    }

    public PaymentService(ChargeService charges, PaymentRepository payments, BillingEventListener events) {
        this.charges = Objects.requireNonNull(charges, "charges");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.events = Objects.requireNonNull(events, "events");
    }

    public List<Payment> history(String memberId) {
        return payments.findAll().stream().filter(payment -> payment.memberId().equals(memberId))
                .sorted(Comparator.comparing(Payment::paidAt).reversed()).toList();
    }

    /** Allocates a receipt across selected unpaid charges, oldest first. */
    public Receipt pay(User actor, String memberId, List<String> selectedChargeIds, BigDecimal amount,
            PaymentMethod method) throws IOException {
        requireCirculation(actor);
        Objects.requireNonNull(selectedChargeIds, "selectedChargeIds");
        Objects.requireNonNull(method, "method");
        BigDecimal remaining = ChargeCalculator.money(amount);
        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException("Payment must be greater than zero.");
        }
        List<Charge> oldCharges = charges.allRecords();
        List<Charge> selected = oldCharges.stream()
                .filter(charge -> charge.memberId().equals(memberId))
                .filter(charge -> selectedChargeIds.contains(charge.chargeId()))
                .filter(charge -> charge.type() != ChargeType.REFUND && charge.balance().signum() > 0)
                .sorted(Comparator.comparing(Charge::createdAt).thenComparing(Charge::chargeId)).toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one unpaid charge.");
        }
        BigDecimal selectedBalance = selected.stream().map(Charge::balance)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (remaining.compareTo(selectedBalance) > 0) {
            throw new IllegalArgumentException("Payment cannot exceed the selected charges' total balance.");
        }

        Map<String, Charge> updatedById = new HashMap<>();
        List<Payment> created = new ArrayList<>();
        LocalDateTime paidAt = LocalDateTime.now();
        for (Charge charge : selected) {
            if (remaining.signum() == 0)
                break;
            BigDecimal applied = remaining.min(charge.balance());
            updatedById.put(charge.chargeId(), charge.withAmounts(charge.waivedAmount(),
                    charge.paidAmount().add(applied)));
            created.add(new Payment(UUID.randomUUID().toString(), memberId, charge.chargeId(), applied,
                    method, actor.username(), paidAt));
            remaining = remaining.subtract(applied);
        }
        List<Charge> updatedCharges = oldCharges.stream()
                .map(charge -> updatedById.getOrDefault(charge.chargeId(), charge)).toList();
        saveTogether(oldCharges, updatedCharges, created);
        created.forEach(events::onPaymentReceived);
        return new Receipt(created.getFirst().paymentId(), created);
    }

    /**
     * Records an administrator-authorized negative payment against a refund credit.
     */
    public List<Payment> recordRefund(User actor, String memberId, String chargeId, BigDecimal amount,
            PaymentMethod method) throws IOException {
        if (actor == null || !actor.active() || actor.role() != User.Role.ADMIN) {
            throw new SecurityException("Only an administrator can record refunds.");
        }
        Objects.requireNonNull(method, "method");
        BigDecimal refundAmount = ChargeCalculator.money(amount);
        if (refundAmount.signum() <= 0) {
            throw new IllegalArgumentException("Refund payment must be greater than zero.");
        }
        List<Charge> oldCharges = charges.allRecords();
        Charge refund = oldCharges.stream().filter(charge -> charge.chargeId().equals(chargeId)
                && charge.memberId().equals(memberId) && charge.type() == ChargeType.REFUND).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Refund credit does not exist."));
        BigDecimal due = refund.balance().negate();
        if (due.signum() <= 0 || refundAmount.compareTo(due) > 0) {
            throw new IllegalArgumentException("Refund payment exceeds the remaining credit.");
        }
        Charge updated = refund.withAmounts(refund.waivedAmount(), refund.paidAmount().subtract(refundAmount));
        List<Charge> updatedCharges = oldCharges.stream()
                .map(charge -> charge.chargeId().equals(chargeId) ? updated : charge).toList();
        Payment payment = new Payment(UUID.randomUUID().toString(), memberId, chargeId, refundAmount.negate(),
                method, actor.username(), LocalDateTime.now());
        saveTogether(oldCharges, updatedCharges, List.of(payment));
        events.onPaymentReceived(payment);
        return List.of(payment);
    }

    private void saveTogether(List<Charge> oldCharges, List<Charge> updatedCharges, List<Payment> additions)
            throws IOException {
        List<Payment> oldPayments = payments.findAll();
        List<Payment> updatedPayments = new ArrayList<>(oldPayments);
        updatedPayments.addAll(additions);
        try {
            charges.replaceAll(updatedCharges);
            payments.saveAll(updatedPayments);
        } catch (IOException | RuntimeException exception) {
            try {
                charges.replaceAll(oldCharges);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            try {
                payments.saveAll(oldPayments);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
    }

    private static void requireCirculation(User actor) {
        if (actor == null || !actor.active() || !new PermissionPolicy().canCirculate(actor)) {
            throw new SecurityException("Only staff or an administrator can collect payments.");
        }
    }
}
