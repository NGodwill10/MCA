import data.CsvBookRepository;
import data.CsvMemberRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Member;
import model.MemberStatus;
import model.MembershipType;

/** One-time development data loader; it appends only records with its sample IDs. */
public final class PopulateSampleData {
    private static final String SAMPLE_PREFIX = "SAMPLE-";

    private PopulateSampleData() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: PopulateSampleData <storage-folder>");
        Path storage = Path.of(args[0]).toAbsolutePath().normalize();
        CsvBookRepository books = new CsvBookRepository(storage.resolve("books.txt"));
        CsvMemberRepository members = new CsvMemberRepository(storage.resolve("members.txt"));
        List<Book> bookRows = new ArrayList<>(books.findAll());
        List<Member> memberRows = new ArrayList<>(members.findAll());
        int booksAdded = addBooks(bookRows);
        int membersAdded = addMembers(memberRows);
        if (booksAdded > 0) books.saveAll(bookRows);
        if (membersAdded > 0) members.saveAll(memberRows);
        System.out.println("Added " + membersAdded + " members and " + booksAdded + " books to " + storage);
    }

    private static int addMembers(List<Member> rows) {
        String[][] people = {
                { "Aarav Mehta", "9876500001", "aarav.mehta@example.invalid", "Mumbai" },
                { "Ananya Iyer", "9876500002", "ananya.iyer@example.invalid", "Chennai" },
                { "Vikram Singh", "9876500003", "vikram.singh@example.invalid", "Delhi" },
                { "Kavya Nair", "9876500004", "kavya.nair@example.invalid", "Kochi" },
                { "Rohan Patel", "9876500005", "rohan.patel@example.invalid", "Ahmedabad" },
                { "Ishita Rao", "9876500006", "ishita.rao@example.invalid", "Bengaluru" },
                { "Arjun Das", "9876500007", "arjun.das@example.invalid", "Kolkata" },
                { "Meera Joshi", "9876500008", "meera.joshi@example.invalid", "Pune" }
        };
        LocalDate today = LocalDate.now();
        int added = 0;
        for (int index = 0; index < people.length; index++) {
            String id = SAMPLE_PREFIX + "MEM-" + String.format("%02d", index + 1);
            if (rows.stream().anyMatch(member -> member.memberId().equals(id))) continue;
            String[] person = people[index];
            LocalDateTime now = LocalDateTime.now();
            rows.add(new Member(id, person[0], person[1], person[2], person[3], MembershipType.INDIVIDUAL,
                    today, today.plusDays(365), MemberStatus.ACTIVE, now, now));
            added++;
        }
        return added;
    }

    private static int addBooks(List<Book> rows) {
        String[][] books = {
                { "The God of Small Things", "Arundhati Roy", "Literary Fiction" },
                { "The White Tiger", "Aravind Adiga", "Indian Fiction" },
                { "Train to Pakistan", "Khushwant Singh", "Historical Fiction" },
                { "The Discovery of India", "Jawaharlal Nehru", "Indian History" },
                { "India After Gandhi", "Ramachandra Guha", "History" },
                { "Wings of Fire", "A. P. J. Abdul Kalam", "Biography" },
                { "The Story of My Experiments with Truth", "M. K. Gandhi", "Autobiography" },
                { "Malgudi Days", "R. K. Narayan", "Short Stories" },
                { "The Immortals of Meluha", "Amish Tripathi", "Mythology" },
                { "Midnight's Children", "Salman Rushdie", "Magical Realism" },
                { "The Palace of Illusions", "Chitra Banerjee Divakaruni", "Epic Retelling" },
                { "A Suitable Boy", "Vikram Seth", "Romance" },
                { "The Namesake", "Jhumpa Lahiri", "Diaspora" },
                { "The Argumentative Indian", "Amartya Sen", "Essays" },
                { "The Art of Computer Programming", "Donald Knuth", "Computer Science" },
                { "Clean Code", "Robert C. Martin", "Software Engineering" },
                { "Introduction to Algorithms", "Thomas H. Cormen", "Algorithms" },
                { "Cosmos", "Carl Sagan", "Astronomy" },
                { "The Selfish Gene", "Richard Dawkins", "Biology" },
                { "Sapiens", "Yuval Noah Harari", "Anthropology" }
        };
        int added = 0;
        for (int index = 0; index < books.length; index++) {
            String id = SAMPLE_PREFIX + "BOOK-" + String.format("%02d", index + 1);
            if (rows.stream().anyMatch(book -> book.bookId().equals(id))) continue;
            String[] book = books[index];
            rows.add(new Book(id, "978000" + String.format("%07d", index + 1), book[0], book[1], "Sample Press",
                    "2024", "1", book[2], "English", "S-" + String.format("%02d", index + 1),
                    new BigDecimal("450.00"), 3, 3, LocalDate.now()));
            added++;
        }
        return added;
    }
}
