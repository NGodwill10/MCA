package data;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Contact;

public final class CsvContactRepository implements ContactRepository {
    private static final List<String> HEADER = List.of(
            "contactId", "name", "phone", "email", "notes", "createdAt", "updatedAt", "onlineUserId",
            "schemaVersion");

    private final Path file;
    private List<Contact> contacts;

    public CsvContactRepository(Path file) throws IOException {
        this.file = file;
        contacts = loadContacts();
        if (!CsvUtil.hasHeaderColumn(file, "schemaVersion")) {
            writeContacts();
        }
    }

    @Override
    public List<Contact> findAll() {
        return List.copyOf(contacts);
    }

    @Override
    public void saveAll(List<Contact> contacts) throws IOException {
        List<Contact> updatedContacts = List.copyOf(contacts);
        writeContacts(updatedContacts);
        this.contacts = updatedContacts;
    }

    private List<Contact> loadContacts() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Contact> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new Contact(value(row, header, "contactId"), value(row, header, "name"),
                        value(row, header, "phone"), value(row, header, "email"), value(row, header, "notes"),
                        LocalDateTime.parse(value(row, header, "createdAt")),
                        LocalDateTime.parse(value(row, header, "updatedAt")),
                        value(row, header, "onlineUserId")));
            } catch (RuntimeException exception) {
                System.getLogger(CsvContactRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed contact row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void writeContacts() throws IOException {
        writeContacts(contacts);
    }

    private void writeContacts(List<Contact> records) throws IOException {
        List<List<String>> rows = records.stream().map(contact -> List.of(
                contact.contactId(), contact.name(), contact.phone(), contact.email(), contact.notes(),
                contact.createdAt().toString(), contact.updatedAt().toString(), contact.onlineUserId(),
                CsvUtil.SCHEMA_VERSION)).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }
}