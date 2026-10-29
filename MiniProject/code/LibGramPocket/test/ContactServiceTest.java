import data.CsvContactRepository;
import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import model.Contact;
import services.ContactSearchField;
import services.ContactService;
import services.DuplicateContactException;

public final class ContactServiceTest {
    private ContactServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgram-pocket-contacts-phase3-");
        try {
            verifyValidationDuplicatesAndUpdates(directory);
            verifySearchSortAndIsolation(directory);
            verifyCsvRoundTrip(directory);
            System.out.println("Pocket ContactService tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyValidationDuplicatesAndUpdates(Path directory) throws Exception {
        Path file = directory.resolve("contact-rules.txt");
        CsvContactRepository repository = new CsvContactRepository(file);
        ContactService service = new ContactService(repository);
        Contact ravi = service.add(draft(" Ravi ", " +1 (212) 555-0123 ", "RAVI@Example.com", "Neighbour"));
        assertEquals("Ravi", ravi.name(), "Name is trimmed");
        assertEquals("+12125550123", ravi.phone(), "Phone is stored normalized");
        assertEquals("ravi@example.com", ravi.email(), "Email is stored lower-case");
        assertTrue(!ravi.contactId().isBlank(), "Contact ID is generated");

        try {
            service.add(draft("Ravi duplicate", "12125550123", "", ""));
            throw new AssertionError("Duplicate phone must request confirmation");
        } catch (DuplicateContactException exception) {
            assertEquals(ravi.contactId(), exception.existingContact().contactId(), "Duplicate identifies the match");
        }
        Contact accepted = service.add(draft("Ravi duplicate", "12125550123", "", ""), true);
        Contact sameName = service.add(draft("Ravi", "", "", ""));
        assertTrue(!accepted.contactId().equals(sameName.contactId()), "Duplicate names are permitted");

        expectInvalid(() -> service.add(draft("", "", "", "")), "Name is required");
        expectInvalid(() -> service.add(draft("Bad phone", "1234", "", "")), "Short phone is rejected");
        expectInvalid(() -> service.add(draft("Bad email", "", "bad@address", "")), "Malformed email is rejected");
        expectInvalid(() -> service.add(draft("N".repeat(101), "", "", "")), "Long name is rejected");
        expectInvalid(() -> service.add(draft("Long notes", "", "", "n".repeat(501))), "Long notes are rejected");

        Contact withReservedId = new Contact(ravi.contactId(), ravi.name(), ravi.phone(), ravi.email(), ravi.notes(),
                ravi.createdAt(), ravi.updatedAt(), "online-user-42");
        repository.saveAll(repository.findAll().stream()
                .map(contact -> contact.contactId().equals(ravi.contactId()) ? withReservedId : contact).toList());
        Contact updated = service.update(draftWithId(ravi.contactId(), "Ravi Updated", "", "", "Updated note"));
        assertEquals(ravi.contactId(), updated.contactId(), "Update preserves ID");
        assertEquals(ravi.createdAt(), updated.createdAt(), "Update preserves creation time");
        assertEquals("online-user-42", updated.onlineUserId(), "Update preserves reserved online ID");
        CsvContactRepository restarted = new CsvContactRepository(file);
        assertEquals("online-user-42", restarted.findAll().stream()
                .filter(contact -> contact.contactId().equals(ravi.contactId())).findFirst().orElseThrow()
                .onlineUserId(), "Reserved online ID survives CSV round-trip");

        service.delete(accepted.contactId());
        assertTrue(service.getById(accepted.contactId()).isEmpty(), "Delete removes the record");
    }

    private static void verifySearchSortAndIsolation(Path directory) throws Exception {
        ContactService accountA = new ContactService(new CsvContactRepository(directory.resolve("account-a.txt")));
        ContactService accountB = new ContactService(new CsvContactRepository(directory.resolve("account-b.txt")));
        accountA.add(draft("Zara", "555-123-4567", "zara@example.org", "Book club friend"));
        accountA.add(draft("Asha", "+44 (20) 1234 5678", "asha@example.org", "Ravi's neighbour"));
        assertEquals("Asha", accountA.listSorted().getFirst().name(), "Default sort is name A-Z");
        assertEquals(1, accountA.search(ContactSearchField.NAME, "zArA").size(), "Name search ignores case");
        assertEquals(1, accountA.search(ContactSearchField.PHONE, "555 123").size(),
                "Phone search normalizes spaces and punctuation");
        assertEquals(1, accountA.search(ContactSearchField.EMAIL, "ASHA@").size(), "Email search matches");
        assertEquals(1, accountA.search(ContactSearchField.NOTES, "book club").size(), "Notes search matches");
        assertEquals(2, accountA.search(ContactSearchField.ALL, "example").size(), "All search spans fields");
        assertTrue(accountB.listSorted().isEmpty(), "Account B cannot see account A's contacts");
    }

    private static void verifyCsvRoundTrip(Path directory) throws Exception {
        Path file = directory.resolve("contact-notes.txt");
        ContactService service = new ContactService(new CsvContactRepository(file));
        String notes = "Neighbour, \"flat 4B\"\nCall after 6";
        service.add(draft("Quoted notes", "", "", notes));
        Contact loaded = new CsvContactRepository(file).findAll().getFirst();
        assertEquals(notes, loaded.notes(), "CSV preserves commas, quotes and line breaks");
        assertEquals(2, CsvUtil.read(file).size(), "CSV has a header and one contact record");
    }

    private static Contact draft(String name, String phone, String email, String notes) {
        return draftWithId("", name, phone, email, notes);
    }

    private static Contact draftWithId(String id, String name, String phone, String email, String notes) {
        LocalDateTime now = LocalDateTime.now();
        return new Contact(id, name, phone, email, notes, now, now, "");
    }

    private static void expectInvalid(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}