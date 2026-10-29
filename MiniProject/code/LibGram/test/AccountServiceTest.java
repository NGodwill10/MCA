import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import model.User;
import model.User.Role;
import services.AccountService;
import services.AuthService;

public final class AccountServiceTest {
    private AccountServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgram-accounts-phase2-");
        try {
            CsvUserRepository repository = new CsvUserRepository(directory.resolve("users.txt"));
            AuthService auth = new AuthService(repository);
            User admin = auth.createInitialAdmin("admin", "Admin", "admin-pass".toCharArray());
            AccountService accounts = new AccountService(repository);
            User staff = accounts.createStaff(admin, "staff", "Staff User", "temporary".toCharArray());
            assertTrue(new AuthService(repository).authenticate("staff", "temporary".toCharArray()).isPresent(),
                    "Staff can log in with temporary password");
            accounts.resetPassword(admin, staff.userId(), "new-password".toCharArray());
            assertTrue(new AuthService(repository).authenticate("staff", "new-password".toCharArray()).isPresent(),
                    "Reset password works");
            accounts.setActive(admin, staff.userId(), false);
            assertTrue(new AuthService(repository).authenticate("staff", "new-password".toCharArray()).isEmpty(),
                    "Disabled staff cannot log in");
            try {
                accounts.setActive(admin, admin.userId(), false);
                throw new AssertionError("Last active admin must not be disabled");
            } catch (IllegalArgumentException expected) {
                // expected
            }
            System.out.println("Account service tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
