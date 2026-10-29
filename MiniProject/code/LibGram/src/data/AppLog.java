package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Central application logging facade. Sensitive values must never be passed to it. */
public final class AppLog {
    private static final Logger LOGGER = Logger.getLogger("libgram");
    private static boolean configured;
    private AppLog() { }
    public static synchronized void configure(Path storage) {
        if (configured) return;
        try {
            Path logs = storage.resolve("logs");
            Files.createDirectories(logs);
            FileHandler handler = new FileHandler(logs.resolve("libgram-" + LocalDate.now() + ".log").toString(), true);
            handler.setFormatter(new SimpleFormatter());
            LOGGER.setUseParentHandlers(false);
            LOGGER.addHandler(handler);
            configured = true;
            try (var files = Files.list(logs)) {
                files.filter(path -> path.getFileName().toString().startsWith("libgram-"))
                        .filter(path -> path.toFile().lastModified() < System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000)
                        .forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
            }
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Unable to configure file logging", exception);
        }
    }
    public static void info(String message) { LOGGER.log(Level.INFO, message); }
    public static void warning(String message, Throwable cause) { LOGGER.log(Level.WARNING, message, cause); }
    public static void error(String message, Throwable cause) { LOGGER.log(Level.SEVERE, message, cause); }
}
