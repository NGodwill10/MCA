package data;

import java.io.IOException;
import java.util.List;
import model.Payment;

public interface PaymentRepository {
    List<Payment> findAll();
    void saveAll(List<Payment> payments) throws IOException;
}
