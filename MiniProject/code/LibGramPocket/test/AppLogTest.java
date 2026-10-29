import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import services.AppLog;

public final class AppLogTest {
    public static void main(String[] args) throws Exception {
        Path storage = Files.createTempDirectory("libgrampocket-log-test-");
        try {
            Path logs = storage.resolve("logs");
            Files.createDirectories(logs);
            Path expired = logs.resolve("libgrampocket-" + LocalDate.now().minusDays(14) + ".log");
            Path retained = logs.resolve("libgrampocket-" + LocalDate.now().minusDays(13) + ".log");
            Files.writeString(expired, "old\n");
            Files.writeString(retained, "recent\n");
            AppLog.configure(storage);
            check(!Files.exists(expired));
            check(Files.exists(retained));
            System.getLogger(AppLogTest.class.getName()).log(System.Logger.Level.WARNING, "test warning");
            Path log = storage.resolve("logs/libgrampocket-" + LocalDate.now() + ".log");
            check(Files.readString(log).contains("test warning"));
            System.out.println("AppLogTest passed");
        } finally {
            try (var paths = Files.walk(storage)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Application log assertion failed");
        }
    }
}