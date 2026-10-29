package data;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.User;
import model.User.Role;

public final class CsvUserRepository implements UserRepository {
    private static final List<String> HEADER = List.of(
            "userId", "username", "displayName", "role", "salt", "passwordHash", "active", "createdAt");

    private final Path file;
    private final List<User> users;

    public CsvUserRepository(Path file) throws IOException {
        this.file = file;
        this.users = new ArrayList<>(loadUsers());
    }

    @Override
    public List<User> findAll() {
        return List.copyOf(users);
    }

    @Override
    public void save(User user) throws IOException {
        users.removeIf(existing -> existing.userId().equals(user.userId()));
        users.add(user);
        List<List<String>> rows = users.stream().map(existing -> List.of(
                existing.userId(), existing.username(), existing.displayName(), existing.role().name(),
                existing.salt(), existing.passwordHash(), Boolean.toString(existing.active()),
                existing.createdAt().toString())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private List<User> loadUsers() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<User> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new User(
                        value(row, header, "userId"), value(row, header, "username"),
                        value(row, header, "displayName"), Role.valueOf(value(row, header, "role")),
                        value(row, header, "salt"), value(row, header, "passwordHash"),
                        Boolean.parseBoolean(value(row, header, "active")),
                        LocalDateTime.parse(value(row, header, "createdAt"))));
            } catch (IllegalArgumentException exception) {
                System.getLogger(CsvUserRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed user row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return loaded;
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }
}