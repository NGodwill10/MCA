package data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Process lock for one LibGram storage directory. */
public final class StorageLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private StorageLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
    public static StorageLock acquire(Path storage) throws IOException {
        FileChannel channel = FileChannel.open(storage.resolve(".libgram.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) { channel.close(); throw new IOException("LibGram is already running"); }
            return new StorageLock(channel, lock);
        } catch (java.nio.channels.OverlappingFileLockException exception) {
            channel.close();
            throw new IOException("LibGram is already running", exception);
        }
    }
    @Override public void close() throws IOException { lock.release(); channel.close(); }
}
