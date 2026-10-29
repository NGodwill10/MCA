import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import services.AuthService;

public final class AuthServiceTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgrampocket-auth-test-");
        try {
            Path usersFile = directory.resolve("users.txt");
            AuthService auth = new AuthService(new CsvUserRepository(usersFile));
            expectInvalid(() -> auth.signUp("reader", "", "short".toCharArray(), "short".toCharArray()));
            auth.signUp("reader", "Reader", "original-password".toCharArray(),
                    "original-password".toCharArray());
            expectInvalid(() -> auth.changePassword("incorrect-password".toCharArray(),
                    "replacement-password".toCharArray(), "replacement-password".toCharArray()));
            expectInvalid(() -> auth.changePassword("original-password".toCharArray(),
                    "replacement-password".toCharArray(), "different-password".toCharArray()));
            auth.changePassword("original-password".toCharArray(), "replacement-password".toCharArray(),
                    "replacement-password".toCharArray());
            check(!Files.readString(usersFile).contains("replacement-password"));

            AuthService restarted = new AuthService(new CsvUserRepository(usersFile));
            check(restarted.authenticate("reader", "original-password".toCharArray()).isEmpty());
            check(restarted.authenticate("reader", "replacement-password".toCharArray()).isPresent());
            System.out.println("AuthServiceTest passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void expectInvalid(CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected invalid authentication data");
        } catch (IllegalArgumentException expected) {
            // Expected invalid input.
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Authentication assertion failed");
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}