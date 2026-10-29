package services;

import java.io.IOException;
import model.NotificationContext;
import model.NotificationType;
import model.Reservation;

public final class ReservationNotificationListener implements ReservationEventListener {
    private final NotificationService notifications;
    public ReservationNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onReservationReady(Reservation reservation) {
        try { notifications.addEvent(NotificationContext.LENDING, NotificationType.RESERVATION_READY,
                "Reservation ready: " + reservation.bookTitleSnapshot() + " for " + reservation.memberId(), reservation.reservationId());
        } catch (IOException exception) { throw new IllegalStateException("Could not write reservation notification.", exception); }
    }
    @Override public void onReservationCreated(Reservation reservation) { write(NotificationType.RESERVATION_CREATED, "Reservation created for " + reservation.bookTitleSnapshot() + " by " + reservation.memberId(), reservation); }
    @Override public void onReservationExpired(Reservation reservation) { write(NotificationType.RESERVATION_EXPIRED, "Reservation expired: " + reservation.bookTitleSnapshot(), reservation); }
    @Override public void onReservationCollected(Reservation reservation) { write(NotificationType.RESERVATION_COLLECTED, "Reservation collected: " + reservation.bookTitleSnapshot(), reservation); }
    private void write(NotificationType type, String message, Reservation reservation) {
        try { notifications.addEvent(NotificationContext.LENDING, type, message, reservation.reservationId()); }
        catch (IOException exception) { throw new IllegalStateException("Could not write reservation notification.", exception); }
    }
}
