package data;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class CsvUtil {
    private static final DateTimeFormatter CORRUPT_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    public static final String SCHEMA_VERSION = "1";
    private static final int DAILY_BACKUP_RETENTION = 5;

    private CsvUtil() {
    }

    public static List<List<String>> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return parse(file, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException primaryFailure) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (Files.exists(backup)) {
                List<List<String>> recovered;
                try {
                    recovered = parse(backup, Files.readString(backup, StandardCharsets.UTF_8));
                } catch (IOException backupFailure) {
                    primaryFailure.addSuppressed(backupFailure);
                    quarantine(file);
                    quarantine(backup);
                    System.getLogger(CsvUtil.class.getName()).log(System.Logger.Level.ERROR,
                            "Both data file and backup are unreadable; starting empty: " + file, primaryFailure);
                    return List.of();
                }
                quarantine(file);
                restoreBackup(backup, file);
                System.getLogger(CsvUtil.class.getName()).log(System.Logger.Level.WARNING,
                        "Recovered " + file + " from its backup");
                return recovered;
            }
            quarantine(file);
            System.getLogger(CsvUtil.class.getName()).log(System.Logger.Level.ERROR,
                    "Data file is unreadable and has no usable backup; starting empty: " + file, primaryFailure);
            return List.of();
        }
    }

    public static boolean hasHeaderColumn(Path file, String column) throws IOException {
        List<List<String>> records = read(file);
        return !records.isEmpty() && records.getFirst().contains(column);
    }

    private static List<List<String>> parse(Path file, String content) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                record.add(field.toString());
                records.add(List.copyOf(record));
                record.clear();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(current);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IOException("Malformed CSV: unterminated quoted field in " + file);
        }
        if (field.length() > 0 || fieldStarted || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return List.copyOf(records);
    }

    public static void createDailyBackups(Path accountDirectory, LocalDate backupDate) throws IOException {
        if (!Files.isDirectory(accountDirectory)) {
            return;
        }
        Path backupDirectory = accountDirectory.resolve("backups");
        List<Path> files;
        try (Stream<Path> paths = Files.list(accountDirectory)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt")).toList();
        }
        if (files.isEmpty()) {
            return;
        }
        Files.createDirectories(backupDirectory);
        String date = backupDate.toString();
        for (Path file : files) {
            String name = file.getFileName().toString();
            Path backup = backupDirectory.resolve(name + "." + date + ".bak");
            if (!Files.exists(backup)) {
                Files.copy(file, backup);
            }
            List<Path> historical;
            try (Stream<Path> paths = Files.list(backupDirectory)) {
                historical = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(name + "."))
                        .filter(path -> path.getFileName().toString().endsWith(".bak"))
                        .sorted(java.util.Comparator.comparing(Path::getFileName).reversed()).toList();
            }
            for (Path oldBackup : historical.stream().skip(DAILY_BACKUP_RETENTION).toList()) {
                Files.deleteIfExists(oldBackup);
            }
        }
    }

    private static void restoreBackup(Path backup, Path file) throws IOException {
        Path temporaryFile = Files.createTempFile(file.toAbsolutePath().getParent(), file.getFileName().toString(),
                ".restore");
        try {
            Files.copy(backup, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void quarantine(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        String suffix = ".corrupt-" + LocalDateTime.now().format(CORRUPT_SUFFIX);
        Path quarantine = file.resolveSibling(file.getFileName() + suffix);
        int duplicate = 1;
        while (Files.exists(quarantine)) {
            quarantine = file.resolveSibling(file.getFileName() + suffix + "-" + duplicate++);
        }
        Files.move(file, quarantine);
    }

    public static void writeAtomically(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Path absoluteFile = file.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, absoluteFile.getFileName().toString(), ".tmp");
        Path backupFile = absoluteFile.resolveSibling(absoluteFile.getFileName() + ".bak");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writeRecord(writer, header);
                for (List<String> row : rows) {
                    writeRecord(writer, row);
                }
            }
            if (Files.exists(absoluteFile)) {
                Files.copy(absoluteFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void writeRecord(BufferedWriter writer, List<String> fields) throws IOException {
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            String value = fields.get(index);
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(value);
            }
        }
        writer.newLine();
    }
}