package data;

import java.io.IOException;
import java.util.List;
import model.Charge;

public interface ChargeRepository {
    List<Charge> findAll();
    void saveAll(List<Charge> charges) throws IOException;
}
