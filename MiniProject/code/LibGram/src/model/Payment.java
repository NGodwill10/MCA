package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Payment(String paymentId, String memberId, String chargeId, BigDecimal amount,
        PaymentMethod method, String receivedBy, LocalDateTime paidAt) { }
