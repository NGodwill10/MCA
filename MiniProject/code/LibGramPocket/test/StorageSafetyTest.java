import data.CsvUtil;
import data.StorageLock;
import data.StoragePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

public final class StorageSafetyTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgrampocket-storage-safety-");
        try {
            verifyLocking(directory.resolve("storage"));
            verifyDailyBackupRetention(directory.resolve("account"));
            verifyStorageOverride();
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("StorageSafetyTest passed");
    }

    private static void verifyLocking(Path directory) throws Exception {
        try (StorageLock first = StorageLock.tryAcquire(directory).orElseThrow()) {
            check(first != null);
            check(StorageLock.tryAcquire(directory).isEmpty());
            verifySeparateProcessBlocked(directory);
        }
        try (StorageLock reopened = StorageLock.tryAcquire(directory).orElseThrow()) {
            check(reopened != null);
        }
    }

    private static void verifySeparateProcessBlocked(Path directory) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows")
                        ? "java.exe"
                        : "java")
                .toString();
        Process process = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                "StorageLockProbe", directory.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .trim();
        check(process.waitFor() == 0 && output.equals("BUSY"));
    }

    private static void verifyDailyBackupRetention(Path accountDirectory) throws Exception {
        Files.createDirectories(accountDirectory);
        Path books = accountDirectory.resolve("books.txt");
        for (int day = 1; day <= 7; day++) {
            Files.writeString(books, "version," + day + "\n");
            CsvUtil.createDailyBackups(accountDirectory, LocalDate.of(2026, 10, day));
        }
        Path backupDirectory = accountDirectory.resolve("backups");
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            check(paths.count() == 5);
        }
        check(Files.exists(backupDirectory.resolve("books.txt.2026-10-07.bak")));
        CsvUtil.createDailyBackups(accountDirectory, LocalDate.of(2026, 10, 7));
        try (Stream<Path> paths = Files.list(backupDirectory)) {
            check(paths.count() == 5);
        }
    }

    private static void verifyStorageOverride() throws Exception {
        Path expected = Path.of("storage-demo").toAbsolutePath().normalize();
        check(StoragePaths.storageDirectory("--storage", "storage-demo").equals(expected));
        try {
            StoragePaths.storageDirectory("--other", "storage-demo");
            throw new AssertionError("Unexpected argument should be rejected");
        } catch (IllegalArgumentException expectedFailure) {
            // Expected invalid command-line arguments.
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Storage safety assertion failed");
        }
    }
}