package data;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CsvUtil {
    private static final List<String> RECOVERY_NOTICES = new CopyOnWriteArrayList<>();

    private CsvUtil() {
    }

    public static List<List<String>> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return withoutSchemaComment(parse(Files.readString(file, StandardCharsets.UTF_8), file));
        } catch (IOException exception) {
            AppLog.warning("Unable to read " + file.getFileName() + "; attempting recovery", exception);
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (Files.exists(backup)) {
                try {
                    List<List<String>> recovered = withoutSchemaComment(parse(Files.readString(backup, StandardCharsets.UTF_8), backup));
                    AppLog.warning("Recovered " + file.getFileName() + " from backup", null);
                    RECOVERY_NOTICES.add(file.getFileName() + " was unreadable and was recovered from its backup.");
                    return recovered;
                } catch (IOException backupFailure) {
                    AppLog.warning("Backup for " + file.getFileName() + " is also unreadable", backupFailure);
                }
            }
            Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            AppLog.warning("Renamed corrupt file to " + corrupt.getFileName(), null);
            RECOVERY_NOTICES.add(file.getFileName() + " and its backup were unreadable. The file was preserved as "
                    + corrupt.getFileName() + "; the app started with an empty dataset for it.");
            return List.of();
        }
    }

    /** Returns and clears startup recovery notices for display to an administrator. */
    public static List<String> consumeRecoveryNotices() {
        List<String> notices = List.copyOf(RECOVERY_NOTICES);
        RECOVERY_NOTICES.clear();
        return notices;
    }

    private static List<List<String>> parse(String content, Path file) throws IOException {
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

    public static void writeAtomically(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Path absoluteFile = file.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, absoluteFile.getFileName().toString(), ".tmp");
        Path backupFile = absoluteFile.resolveSibling(absoluteFile.getFileName() + ".bak");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writeRecord(writer, header);
                writer.write("# schemaVersion=1");
                writer.newLine();
                for (List<String> row : rows) {
                    writeRecord(writer, row);
                }
            }
            if (Files.exists(absoluteFile)) {
                Files.copy(absoluteFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                Path backups = parent.resolve("backups");
                Files.createDirectories(backups);
                Path daily = backups.resolve(absoluteFile.getFileName() + "-" + LocalDate.now() + ".bak");
                if (!Files.exists(daily)) Files.copy(absoluteFile, daily);
                try (var files = Files.list(backups)) {
                    files.filter(path -> path.getFileName().toString().startsWith(absoluteFile.getFileName() + "-"))
                            .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                            .skip(5)
                            .forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
                }
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

    private static List<List<String>> withoutSchemaComment(List<List<String>> records) {
        return records.stream().filter(record -> record.size() != 1
                || !record.getFirst().startsWith("# schemaVersion=")).toList();
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
