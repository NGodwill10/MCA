package services;

import java.io.IOException;
import model.Charge;
import model.NotificationContext;
import model.NotificationType;
import model.Payment;

public final class BillingNotificationListener implements BillingEventListener {
    private final NotificationService notifications;
    public BillingNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onPaymentReceived(Payment payment) {
        write(NotificationType.PAYMENT_RECEIVED, "Payment received: Rs. " + payment.amount() + " for member " + payment.memberId(), payment.paymentId());
    }
    @Override public void onFineWaived(Charge charge) {
        write(NotificationType.FINE_WAIVED, "Fine waived: Rs. " + charge.waivedAmount() + " for member " + charge.memberId(), charge.chargeId());
    }
    private void write(NotificationType type, String message, String refId) {
        try { notifications.addEvent(NotificationContext.LENDING, type, message, refId); }
        catch (IOException exception) { throw new IllegalStateException("Could not write billing notification.", exception); }
    }
}
