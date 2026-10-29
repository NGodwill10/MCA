package services;

import model.Reservation;

@FunctionalInterface
public interface ReservationEventListener {
    ReservationEventListener NO_OP = reservation -> { };
    void onReservationReady(Reservation reservation);
    default void onReservationCreated(Reservation reservation) { }
    default void onReservationExpired(Reservation reservation) { }
    default void onReservationCollected(Reservation reservation) { }
}
