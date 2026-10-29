package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record Charge(String chargeId, String memberId, String loanId, ChargeType type,
        String description, BigDecimal amount, BigDecimal waivedAmount, BigDecimal paidAmount,
        ChargeStatus status, LocalDateTime createdAt) {
    public BigDecimal balance() {
        return amount.subtract(waivedAmount).subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public Charge withAmounts(BigDecimal waived, BigDecimal paid) {
        BigDecimal w = money(waived);
        BigDecimal p = money(paid);
        BigDecimal balance = amount.subtract(w).subtract(p).setScale(2, RoundingMode.HALF_UP);
        ChargeStatus next;
        if (amount.signum() < 0) {
            if (balance.signum() > 0 || w.signum() != 0 || p.signum() > 0) {
                throw new IllegalArgumentException("Refund amounts must be paid as negative payments.");
            }
            next = balance.signum() == 0 ? ChargeStatus.PAID
                    : p.signum() < 0 ? ChargeStatus.PARTIAL : ChargeStatus.UNPAID;
        } else {
            if (w.signum() < 0 || p.signum() < 0 || balance.signum() < 0) {
                throw new IllegalArgumentException("Paid or waived amounts cannot exceed a charge.");
            }
            next = balance.signum() == 0
                    ? p.signum() > 0 ? ChargeStatus.PAID : ChargeStatus.WAIVED
                    : p.signum() > 0 ? ChargeStatus.PARTIAL : ChargeStatus.UNPAID;
        }
        return new Charge(chargeId, memberId, loanId, type, description, amount, w, p, next, createdAt);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
