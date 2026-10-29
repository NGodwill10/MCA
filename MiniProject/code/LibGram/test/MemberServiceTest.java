import data.CsvMemberRepository;
import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.User;
import model.User.Role;
import services.DuplicateMemberEmailException;
import services.DuplicateMemberPhoneException;
import services.MemberChangeListener;
import services.MemberSearchField;
import services.MemberService;
import services.MemberStatusFilter;
import services.SettingsDefaults;

public final class MemberServiceTest {
    private MemberServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgram-members-phase3-");
        try {
            verifyValidationDefaultsAndDuplicates(directory);
            verifyStatusChangesPermissionsAndListener(directory);
            verifySearchAndSorting(directory);
            verifyCsvRoundTrip(directory);
            System.out.println("Member service tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyValidationDefaultsAndDuplicates(Path directory) throws Exception {
        MemberService service = new MemberService(new CsvMemberRepository(directory.resolve("validation.txt")));
        Member adminMember = service.add(admin(), draft(" Alice Smith ", " +1 (212) 555-0123 ",
                "ALICE@example.com", "Main Street", MembershipType.INDIVIDUAL, null, null, null));
        assertTrue(adminMember.memberId().matches("MEM-[0-9A-F]{8}"), "ID uses MEM- plus eight hex digits");
        assertEquals("Alice Smith", adminMember.name(), "Name is trimmed");
        assertEquals("+12125550123", adminMember.phone(), "Phone is normalized");
        assertEquals("alice@example.com", adminMember.email(), "Email is lower-case");
        assertEquals(LocalDate.now(), adminMember.joinDate(), "Join date defaults to today");
        assertEquals(LocalDate.now().plusDays(365), adminMember.expiryDate(), "Individual defaults to 365 days");
        assertEquals(MemberStatus.ACTIVE, adminMember.status(), "New member is Active");

        Member temporary = service.add(admin(), draft("Temporary", "5551234567", "temp@example.org", "",
                MembershipType.TEMPORARY, null, null, null));
        assertEquals(temporary.joinDate().plusDays(30), temporary.expiryDate(), "Temporary defaults to 30 days");
        Member family = service.add(admin(), draft("Family", "5551234568", "family@example.org", "",
                MembershipType.FAMILY, null, null, null));
        Member senior = service.add(admin(), draft("Senior", "5551234569", "senior@example.org", "",
                MembershipType.SENIOR, null, null, null));
        assertEquals(family.joinDate().plusDays(365), family.expiryDate(), "Family defaults to 365 days");
        assertEquals(senior.joinDate().plusDays(365), senior.expiryDate(), "Senior defaults to 365 days");
        assertEquals(6, Integer.parseInt(SettingsDefaults.values().get("limit.FAMILY")), "Family limit is six");
        assertEquals(3, Integer.parseInt(SettingsDefaults.values().get("limit.INDIVIDUAL")), "Individual limit is three");
        assertEquals(3, Integer.parseInt(SettingsDefaults.values().get("limit.SENIOR")), "Senior limit is three");
        assertEquals(2, Integer.parseInt(SettingsDefaults.values().get("limit.TEMPORARY")), "Temporary limit is two");

        try {
            service.add(admin(), draft("Same email", "7771234567", "Alice@Example.com", "",
                    MembershipType.FAMILY, null, null, null), true);
            throw new AssertionError("Duplicate email must be rejected even when phone duplicates are confirmed");
        } catch (DuplicateMemberEmailException exception) {
            assertEquals(adminMember.memberId(), exception.existingMember().memberId(),
                    "Email duplicate identifies the existing member");
        }
        try {
            service.add(admin(), draft("Shared phone", "1 212 555 0123", "shared@example.org", "",
                    MembershipType.FAMILY, null, null, null));
            throw new AssertionError("Duplicate phone must request confirmation");
        } catch (DuplicateMemberPhoneException exception) {
            assertEquals(adminMember.name(), exception.existingMember().name(), "Phone duplicate names the match");
        }
        Member sharedPhone = service.add(admin(), draft("Shared phone", "1 212 555 0123", "shared@example.org", "",
                MembershipType.FAMILY, null, null, null), true);
        assertEquals("12125550123", sharedPhone.phone(), "Confirmed phone stores digits without an optional plus");
        Member duplicateName = service.add(admin(), draft("Alice Smith", "5551234570", "same-name@example.org", "",
                MembershipType.INDIVIDUAL, null, null, null));
        assertEquals("Alice Smith", duplicateName.name(), "Duplicate names are allowed");

        expectInvalid(() -> service.add(admin(), draft("No contact", "", "", "", MembershipType.INDIVIDUAL,
                null, null, null)), "Phone or email is required");
        expectInvalid(() -> service.add(admin(), draft("Bad phone", "1234", "valid@example.org", "",
                MembershipType.INDIVIDUAL, null, null, null)), "Four-digit phone is rejected");
        expectInvalid(() -> service.add(admin(), draft("Bad email", "5551234567", "invalid@address", "",
                MembershipType.INDIVIDUAL, null, null, null)), "Malformed email is rejected");
        expectInvalid(() -> service.add(admin(), draft("Future join", "5551234567", "future@example.org", "",
                MembershipType.INDIVIDUAL, LocalDate.now().plusDays(1), null, null)),
                "Future join date is rejected");
        expectInvalid(() -> service.add(admin(), draft("Expiry before join", "5551234567", "dates@example.org", "",
                MembershipType.INDIVIDUAL, LocalDate.now(), LocalDate.now().minusDays(1), null)),
                "Expiry before join date is rejected");
        expectInvalid(() -> service.add(admin(), draft("Long name".repeat(20), "5551234567", "long@example.org", "",
                MembershipType.INDIVIDUAL, null, null, null)), "Long name is rejected");
        expectInvalid(() -> service.add(admin(), draft("Long address", "5551234567", "address@example.org",
                "a".repeat(301), MembershipType.INDIVIDUAL, null, null, null)), "Long address is rejected");
    }

    private static void verifyStatusChangesPermissionsAndListener(Path directory) throws Exception {
        CsvMemberRepository repository = new CsvMemberRepository(directory.resolve("permissions.txt"));
        List<MemberChangeListener.Change> changes = new ArrayList<>();
        MemberService service = new MemberService(repository, new services.PermissionPolicy(),
                (change, member) -> changes.add(change));
        Member created = service.add(admin(), draft("Status Member", "5557654321", "status@example.org", "Address",
                MembershipType.SENIOR, null, null, null));
        Member deactivated = service.deactivate(admin(), created.memberId());
        assertEquals(MemberStatus.INACTIVE, deactivated.status(), "Deactivate sets Inactive");
        assertEquals(created.name(), deactivated.name(), "Deactivation preserves profile fields");
        assertEquals(created.createdAt(), deactivated.createdAt(), "Deactivation preserves creation time");
        Member reactivated = service.reactivate(admin(), created.memberId());
        assertEquals(MemberStatus.ACTIVE, reactivated.status(), "Reactivate sets Active");
        assertEquals(List.of(MemberChangeListener.Change.ADDED, MemberChangeListener.Change.DEACTIVATED,
                MemberChangeListener.Change.REACTIVATED), changes, "Member changes fire listener events");

        User staff = user(Role.STAFF, true);
        assertEquals(1, service.listSorted(staff).size(), "Staff can list members");
        assertTrue(service.getById(staff, created.memberId()).isPresent(), "Staff can view member details");
        assertEquals(1, service.search(staff, MemberSearchField.NAME, "status", MemberStatusFilter.ALL).size(),
                "Staff can search members");
        expectSecurity(() -> service.add(staff, draft("Staff add", "5553332222", "staff@example.org", "",
                MembershipType.INDIVIDUAL, null, null, null)), "Staff cannot add");
        expectSecurity(() -> service.update(staff, reactivated), "Staff cannot update");
        expectSecurity(() -> service.deactivate(staff, created.memberId()), "Staff cannot deactivate");
        expectSecurity(() -> service.reactivate(staff, created.memberId()), "Staff cannot reactivate");
        expectSecurity(() -> service.delete(staff, created.memberId()), "Staff cannot delete");
        expectSecurity(() -> service.listSorted(user(Role.STAFF, false)), "Inactive staff cannot view members");

        service.delete(admin(), created.memberId());
        assertTrue(service.listSorted(admin()).isEmpty(), "Delete removes member");
        assertEquals(MemberChangeListener.Change.DELETED, changes.getLast(), "Delete fires change event");
    }

    private static void verifySearchAndSorting(Path directory) throws Exception {
        MemberService service = new MemberService(new CsvMemberRepository(directory.resolve("search.txt")));
        Member alice = service.add(admin(), draft("Smith, Alice", "+44 (20) 1234 5678", "alice@example.org", "",
                MembershipType.FAMILY, LocalDate.now().minusYears(1), LocalDate.now().plusDays(10), null));
        Member suspended = service.add(admin(), draft("Smith, Brian", "5552223333", "brian@example.org", "",
                MembershipType.SENIOR, LocalDate.now().minusYears(1), LocalDate.now().plusDays(10), null));
        suspended = service.update(admin(), withStatus(suspended, MemberStatus.SUSPENDED));
        Member inactive = service.add(admin(), draft("Taylor, Chris", "5553334444", "chris@example.org", "",
                MembershipType.INDIVIDUAL, LocalDate.now().minusYears(1), LocalDate.now().plusDays(10), null));
        inactive = service.deactivate(admin(), inactive.memberId());
        Member expired = service.add(admin(), draft("Smith, Dana", "5554445555", "dana@example.org", "",
                MembershipType.TEMPORARY, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null));

        assertEquals("Smith, Alice", service.listSorted(admin()).getFirst().name(), "Default sort is name A-Z");
        assertEquals(3, service.search(admin(), MemberSearchField.NAME, "sMiTh", MemberStatusFilter.ALL).size(),
                "Name search ignores case");
        assertEquals(1, service.search(admin(), MemberSearchField.MEMBER_ID, alice.memberId().substring(4),
                MemberStatusFilter.ALL).size(), "Member ID search matches");
        assertEquals(1, service.search(admin(), MemberSearchField.PHONE, "20-1234 5678", MemberStatusFilter.ALL).size(),
                "Phone search normalizes punctuation");
        assertEquals(1,
                service.search(admin(), MemberSearchField.EMAIL, "ALICE@EXAMPLE", MemberStatusFilter.ALL).size(),
                "Email search ignores case");
        assertEquals(1, service.search(admin(), MemberSearchField.MEMBERSHIP_TYPE, "family", MemberStatusFilter.ALL)
                .size(), "Membership type search matches");
        assertEquals(1, service.search(admin(), MemberSearchField.STATUS, "suspended", MemberStatusFilter.ALL).size(),
                "Status text search matches");
        assertEquals(1, service.search(admin(), MemberSearchField.ALL, "alice@example", MemberStatusFilter.ALL).size(),
                "All-field search spans member details");
        assertEquals(2, service.search(admin(), MemberSearchField.NAME, "smith", MemberStatusFilter.ACTIVE).size(),
                "Active filter combines with name search");
        assertEquals(1, service.search(admin(), MemberSearchField.NAME, "smith", MemberStatusFilter.SUSPENDED).size(),
                "Suspended filter combines with name search");
        assertEquals(1, service.search(admin(), MemberSearchField.NAME, "smith", MemberStatusFilter.EXPIRED).size(),
                "Expired filter is derived from expiry date");
        assertEquals(1, service.search(admin(), MemberSearchField.STATUS, "inactive", MemberStatusFilter.INACTIVE)
                .size(), "Inactive status filter finds deactivated members");
        assertTrue(expired.expiredOn(LocalDate.now()), "Expired state is derived, not stored");
        assertEquals(MemberStatus.ACTIVE, expired.status(), "Expired member status remains Active");
    }

    private static void verifyCsvRoundTrip(Path directory) throws Exception {
        Path file = directory.resolve("address.txt");
        MemberService service = new MemberService(new CsvMemberRepository(file));
        String address = "12 Main Street, \"North Wing\"\nNear the library entrance";
        Member added = service.add(admin(), draft("Quoted Address", "5551239876", "address-roundtrip@example.org",
                address, MembershipType.INDIVIDUAL, null, null, null));
        CsvMemberRepository restarted = new CsvMemberRepository(file);
        assertEquals(address, restarted.findAll().getFirst().address(), "CSV preserves commas, quotes and line breaks");
        assertEquals(added.memberId(), restarted.findAll().getFirst().memberId(), "Member persists after restart");
        assertEquals(2, CsvUtil.read(file).size(), "CSV has header and one member record");
    }

    private static Member draft(String name, String phone, String email, String address, MembershipType type,
            LocalDate joinDate, LocalDate expiryDate, MemberStatus status) {
        return new Member("", name, phone, email, address, type, joinDate, expiryDate, status, null, null);
    }

    private static Member withStatus(Member member, MemberStatus status) {
        return new Member(member.memberId(), member.name(), member.phone(), member.email(), member.address(),
                member.membershipType(), member.joinDate(), member.expiryDate(), status, member.createdAt(),
                member.updatedAt());
    }

    private static User admin() {
        return user(Role.ADMIN, true);
    }

    private static User user(Role role, boolean active) {
        return new User("user-1", "librarian", "Library account", role, "salt", "hash", active,
                LocalDateTime.now());
    }

    private static void expectInvalid(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void expectSecurity(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (SecurityException expected) {
            // Expected permission failure.
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
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
