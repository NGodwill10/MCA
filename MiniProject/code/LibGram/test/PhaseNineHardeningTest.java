import data.CsvUtil;
import data.StorageLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PhaseNineHardeningTest {
    private PhaseNineHardeningTest() { }
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-phase9-");
        Path csv = folder.resolve("quoted.txt");
        List<List<String>> rows = List.of(List.of("comma, value", "quote \"value\"", "line\none", " देवनागरी é "));
        CsvUtil.writeAtomically(csv, List.of("a", "b", "c", "d"), rows);
        check(CsvUtil.read(csv).get(1).equals(rows.get(0)), "CSV quoting round-trip failed");
        StorageLock firstLock = StorageLock.acquire(folder);
        try {
            boolean refused = false;
            try {
                StorageLock.acquire(folder).close();
                // A second lock must not be granted.
            } catch (IOException expected) {
                refused = expected.getMessage().contains("already running");
            }
            check(refused, "second storage lock was accepted");
        } finally {
            firstLock.close();
        }
        Files.writeString(csv, "broken,\"unterminated\n");
        Path backup = csv.resolveSibling(csv.getFileName() + ".bak");
        Files.writeString(backup, "a,b,c,d\nrestored,ok,yes,done\n");
        check(CsvUtil.read(csv).get(1).get(0).equals("restored"), "CSV backup recovery failed");
        check(CsvUtil.consumeRecoveryNotices().stream().anyMatch(notice -> notice.contains("quoted.txt")),
                "CSV recovery notice did not name the affected file");
        System.out.println("Phase 9 hardening tests passed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
