package data;

import java.io.IOException;
import java.util.List;
import model.Notification;

public interface NotificationRepository {
    List<Notification> findAll() throws IOException;
    void saveAll(List<Notification> notifications) throws IOException;
}
