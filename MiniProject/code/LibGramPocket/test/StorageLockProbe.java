import data.StorageLock;
import java.nio.file.Path;

public final class StorageLockProbe {
    public static void main(String[] args) throws Exception {
        System.out.println(StorageLock.tryAcquire(Path.of(args[0])).isPresent() ? "ACQUIRED" : "BUSY");
    }
}