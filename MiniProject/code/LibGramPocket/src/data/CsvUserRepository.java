package data;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.User;

public final class CsvUserRepository implements UserRepository {
    private static final List<String> HEADER = List.of(
            "userId", "username", "displayName", "salt", "passwordHash", "createdAt", "schemaVersion");

    private final Path file;
    private final List<User> users;

    public CsvUserRepository(Path file) throws IOException {
        this.file = file;
        this.users = new ArrayList<>(loadUsers());
        if (!CsvUtil.hasHeaderColumn(file, "schemaVersion")) {
            writeUsers();
        }
    }

    @Override
    public List<User> findAll() {
        return List.copyOf(users);
    }

    @Override
    public void save(User user) throws IOException {
        List<User> updatedUsers = new ArrayList<>(users);
        updatedUsers.add(user);
        writeUsers(updatedUsers);
        users.add(user);
    }

    @Override
    public void update(User user) throws IOException {
        List<User> updatedUsers = new ArrayList<>(users);
        int index = -1;
        for (int row = 0; row < updatedUsers.size(); row++) {
            if (updatedUsers.get(row).userId().equals(user.userId())) {
                index = row;
                break;
            }
        }
        if (index < 0) {
            throw new IOException("The account could not be found while saving credentials.");
        }
        updatedUsers.set(index, user);
        writeUsers(updatedUsers);
        users.clear();
        users.addAll(updatedUsers);
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
                loaded.add(new User(value(row, header, "userId"), value(row, header, "username"),
                        value(row, header, "displayName"), value(row, header, "salt"),
                        value(row, header, "passwordHash"),
                        LocalDateTime.parse(value(row, header, "createdAt"))));
            } catch (IllegalArgumentException exception) {
                System.getLogger(CsvUserRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed account row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return loaded;
    }

    private void writeUsers() throws IOException {
        writeUsers(users);
    }

    private void writeUsers(List<User> accounts) throws IOException {
        List<List<String>> rows = accounts.stream().map(user -> List.of(
                user.userId(), user.username(), user.displayName(), user.salt(), user.passwordHash(),
                user.createdAt().toString(), CsvUtil.SCHEMA_VERSION)).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }
}