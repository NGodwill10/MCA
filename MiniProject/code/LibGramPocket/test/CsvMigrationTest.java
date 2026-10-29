import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvNotificationRepository;
import data.CsvSettingsRepository;
import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class CsvMigrationTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgrampocket-migration-test-");
        try {
            verifyOldBookSchema(directory.resolve("books.txt"));
            verifyOldContactSchema(directory.resolve("contacts.txt"));
            verifyOldLoanSchema(directory.resolve("loans.txt"));
            verifyOldNotificationSchema(directory.resolve("notifications.txt"));
            verifyOldUsersSchema(directory.resolve("users.txt"));
            verifyOldSettingsSchema(directory.resolve("settings.txt"));
            System.out.println("CsvMigrationTest passed");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyOldBookSchema(Path file) throws Exception {
        Files.writeString(file, "bookId,ownershipType,lenderContactId,isbn,title,authors,publisher,publicationYear,"
                + "edition,category,language,price,totalCopies,availableCopies,condition,readingStatus,notes,dateAdded\n"
                + "b1,Owned,,isbn,Book,Author,Press,2020,1,Fiction,English,12.00,1,1,Good,Unread,,2020-01-01\n");
        check(new CsvBookRepository(file).findAll().getFirst().title().equals("Book"));
        check(Files.readString(file).lines().findFirst().orElseThrow().endsWith("schemaVersion"));
    }

    private static void verifyOldContactSchema(Path file) throws Exception {
        Files.writeString(file, "contactId,name,phone,email,notes,createdAt,updatedAt\n"
                + "c1,Reader,,,note,2020-01-01T00:00:00,2020-01-01T00:00:00\n");
        check(new CsvContactRepository(file).findAll().getFirst().onlineUserId().isEmpty());
        check(Files.readString(file).lines().findFirst().orElseThrow().endsWith("schemaVersion"));
    }

    private static void verifyOldLoanSchema(Path file) throws Exception {
        Files.writeString(file, "loanId,direction,bookId,bookTitleSnapshot,contactId,issueDate,dueDate,returnDate,"
                + "status,baseCharge,rentCharge,overduePenalty,damageCharge,returnCondition,paymentStatus\n"
                + "l1,LENT,b1,Book,c1,2020-01-01,2020-01-14,,Active,0.00,,,,,None\n");
        check(new CsvLoanRepository(file).findAll().getFirst().pricingSnapshot().isEmpty());
        check(Files.readString(file).lines().findFirst().orElseThrow().endsWith("schemaVersion"));
    }

    private static void verifyOldNotificationSchema(Path file) throws Exception {
        Files.writeString(file, "notificationId,timestamp,context,type,message,refId,dismissed\n"
                + "n1,2020-01-01T00:00:00,BOOKKEEPING,BOOK_ADDED,Added,b1,false\n");
        check(new CsvNotificationRepository(file).findAll().getFirst().severity() == model.NotificationSeverity.INFO);
        check(Files.readString(file).lines().findFirst().orElseThrow().endsWith("schemaVersion"));
    }

    private static void verifyOldUsersSchema(Path file) throws Exception {
        Files.writeString(file, "userId,username,displayName,salt,passwordHash,createdAt\n"
                + "u1,reader,Reader,c2FsdA==,aGFzaA==,2020-01-01T00:00:00\n");
        check(new CsvUserRepository(file).findAll().getFirst().username().equals("reader"));
        check(Files.readString(file).lines().findFirst().orElseThrow().endsWith("schemaVersion"));
    }

    private static void verifyOldSettingsSchema(Path file) throws Exception {
        Files.writeString(file, "key,value\nloan.defaultDays,14\n");
        check(new CsvSettingsRepository(file).load().get("loan.defaultDays").equals("14"));
        check(new CsvSettingsRepository(file).load().get("schemaVersion").equals("1"));
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("CSV migration assertion failed");
        }
    }
}