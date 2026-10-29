package data;

import java.io.IOException;
import java.util.List;
import model.User;

public interface UserRepository {
    List<User> findAll();

    void save(User user) throws IOException;
}