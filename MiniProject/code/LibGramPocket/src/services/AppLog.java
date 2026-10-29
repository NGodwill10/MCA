package services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppLog {
    private static final Pattern LOG_FILE = Pattern.compile("libgrampocket-(\\d{4}-\\d{2}-\\d{2})\\.log");
    private static final int RETAIN_DAYS = 14;
    private static Handler fileHandler;

    private AppLog() {
    }

    public static synchronized void configure(Path storageDirectory) throws IOException {
        Path logDirectory = storageDirectory.resolve("logs");
        Files.createDirectories(logDirectory);
        pruneLogs(logDirectory, LocalDate.now());
        Logger root = Logger.getLogger("");
        root.setLevel(Level.ALL);
        if (fileHandler != null) {
            root.removeHandler(fileHandler);
            fileHandler.close();
        }
        fileHandler = new DailyFileHandler(logDirectory);
        root.addHandler(fileHandler);
    }

    public static void info(Class<?> source, String message) {
        Logger.getLogger(source.getName()).info(message);
    }

    public static void warning(Class<?> source, String message, Throwable cause) {
        Logger.getLogger(source.getName()).log(Level.WARNING, message, cause);
    }

    public static void error(Class<?> source, String message, Throwable cause) {
        Logger.getLogger(source.getName()).log(Level.SEVERE, message, cause);
    }

    private static void pruneLogs(Path directory, LocalDate today) throws IOException {
        LocalDate oldestKept = today.minusDays(RETAIN_DAYS - 1L);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Matcher matcher = LOG_FILE.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                LocalDate date = LocalDate.parse(matcher.group(1), DateTimeFormatter.ISO_LOCAL_DATE);
                if (date.isBefore(oldestKept)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static final class DailyFileHandler extends StreamHandler {
        private final Path directory;
        private LocalDate openedDate;

        private DailyFileHandler(Path directory) throws IOException {
            this.directory = directory;
            setFormatter(new SimpleFormatter());
            setLevel(Level.ALL);
            rotate(LocalDate.now());
        }

        @Override
        public synchronized void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            LocalDate today = LocalDate.now();
            if (!today.equals(openedDate)) {
                try {
                    rotate(today);
                    pruneLogs(directory, today);
                } catch (IOException exception) {
                    System.err.println("LibGramPocket could not rotate its log file.");
                    return;
                }
            }
            super.publish(record);
            flush();
        }

        private void rotate(LocalDate date) throws IOException {
            flush();
            setOutputStream(Files.newOutputStream(directory.resolve("libgrampocket-" + date + ".log"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND));
            openedDate = date;
        }
    }
}