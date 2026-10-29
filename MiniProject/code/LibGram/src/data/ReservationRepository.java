package data;

import java.io.IOException;
import java.util.List;
import model.Reservation;

public interface ReservationRepository {
    List<Reservation> findAll();
    void saveAll(List<Reservation> reservations) throws IOException;
}
