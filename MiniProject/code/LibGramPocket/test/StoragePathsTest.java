import data.StoragePaths;
import java.nio.file.Path;

/** Run from the app folder, its parent, and an unrelated working directory. */
public final class StoragePathsTest {
    public static void main(String[] args) throws Exception {
        Path expected = Path.of(args[0]).toAbsolutePath().normalize().resolve("storage");
        Path actual = StoragePaths.storageDirectory();
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
        System.out.println("App-local storage path verified: " + actual);
    }
}
