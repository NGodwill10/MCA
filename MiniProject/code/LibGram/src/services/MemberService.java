package services;

import data.MemberRepository;
import data.LoanRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.User;

public final class MemberService {
    private static final int NAME_MAX_LENGTH = 100;
    private static final int ADDRESS_MAX_LENGTH = 300;
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private final MemberRepository members;
    private final PermissionPolicy permissions;
    private final MemberChangeListener changeListener;
    private final SettingsService settings;
    private final LoanRepository loans;
    private ChargeService chargeService;
    private ReservationService reservationService;

    public MemberService(MemberRepository members) {
        this(members, defaultSettings(), new PermissionPolicy(), MemberChangeListener.NO_OP);
    }

    public MemberService(MemberRepository members, PermissionPolicy permissions,
            MemberChangeListener changeListener) {
        this(members, defaultSettings(), permissions, changeListener);
    }

    public MemberService(MemberRepository members, SettingsService settings) {
        this(members, settings, new PermissionPolicy(), MemberChangeListener.NO_OP);
    }

    public MemberService(MemberRepository members, SettingsService settings, PermissionPolicy permissions,
            MemberChangeListener changeListener) {
        this(members, settings, permissions, changeListener, null);
    }

    public MemberService(MemberRepository members, SettingsService settings, PermissionPolicy permissions,
            MemberChangeListener changeListener, LoanRepository loans) {
        this.members = Objects.requireNonNull(members, "members");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.loans = loans;
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public void setChargeService(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    public void setReservationService(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Adds a member with default dates and Active status. */
    public Member add(User actor, Member draft) throws IOException {
        return add(actor, draft, false);
    }

    /** Adds a member, optionally confirming a duplicate phone number. */
    public Member add(User actor, Member draft, boolean confirmDuplicatePhone) throws IOException {
        requireManagePermission(actor);
        Member normalized = normalize(draft, true);
        List<Member> oldRecords = members.findAll();
        List<Member> records = new ArrayList<>(oldRecords);
        ensureUniqueEmail(normalized, null, records);
        if (!confirmDuplicatePhone) {
            ensureUniquePhone(normalized, null, records);
        }
        LocalDateTime now = LocalDateTime.now();
        Member created = new Member(generateMemberId(records), normalized.name(), normalized.phone(),
                normalized.email(), normalized.address(), normalized.membershipType(), normalized.joinDate(),
                normalized.expiryDate(), MemberStatus.ACTIVE, now, now);
        records.add(created);
        try {
            members.saveAll(records);
            if (chargeService != null) {
                java.math.BigDecimal registration = settings.getMoney(
                        SettingsDefaults.key("fee.membership.registration.", created.membershipType()));
                if (registration.signum() > 0) {
                    chargeService.add(created.memberId(), "", model.ChargeType.MEMBERSHIP_REGISTRATION,
                            "Membership registration", registration);
                }
            }
        } catch (IOException | RuntimeException exception) {
            try {
                members.saveAll(oldRecords);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
        changeListener.onMemberChanged(MemberChangeListener.Change.ADDED, created);
        return created;
    }

    /**
     * Updates editable member information and preserves ID and creation timestamp.
     */
    public Member update(User actor, Member draft) throws IOException {
        return update(actor, draft, false);
    }

    /** Updates a member, optionally confirming a duplicate phone number. */
    public Member update(User actor, Member draft, boolean confirmDuplicatePhone) throws IOException {
        requireManagePermission(actor);
        Member normalized = normalize(draft, false);
        List<Member> records = new ArrayList<>(members.findAll());
        int index = findIndex(records, draft.memberId());
        if (index < 0) {
            throw new IllegalArgumentException("The selected member no longer exists.");
        }
        Member existing = records.get(index);
        ensureUniqueEmail(normalized, existing.memberId(), records);
        if (!confirmDuplicatePhone) {
            ensureUniquePhone(normalized, existing.memberId(), records);
        }
        Member updated = new Member(existing.memberId(), normalized.name(), normalized.phone(), normalized.email(),
                normalized.address(), normalized.membershipType(), normalized.joinDate(), normalized.expiryDate(),
                normalized.status(), existing.createdAt(), LocalDateTime.now());
        records.set(index, updated);
        members.saveAll(records);
        changeListener.onMemberChanged(MemberChangeListener.Change.UPDATED, updated);
        return updated;
    }

    /** Deactivates a member by setting status to Inactive. */
    public Member deactivate(User actor, String memberId) throws IOException {
        return changeStatus(actor, memberId, MemberStatus.INACTIVE, MemberChangeListener.Change.DEACTIVATED);
    }

    /** Reactivates a member by setting status to Active. */
    public Member reactivate(User actor, String memberId) throws IOException {
        return changeStatus(actor, memberId, MemberStatus.ACTIVE, MemberChangeListener.Change.REACTIVATED);
    }

    /** Permanently deletes a member. */
    public Member delete(User actor, String memberId) throws IOException {
        requireManagePermission(actor);
        List<Member> records = new ArrayList<>(members.findAll());
        int index = findIndex(records, memberId);
        if (index < 0) {
            throw new IllegalArgumentException("The selected member no longer exists.");
        }
        Member deleted = records.remove(index);
        if (loans != null) {
            List<String> active = loans.findAll().stream().filter(loan -> loan.memberId().equals(memberId)
                    && loan.status() == model.LoanStatus.ACTIVE).map(loan -> loan.bookTitleSnapshot()).toList();
            if (!active.isEmpty()) {
                throw new IllegalArgumentException("Cannot delete this member while active loans exist: " + active);
            }
        }
        if (chargeService != null && chargeService.unpaidBalance(memberId).signum() > 0) {
            throw new IllegalArgumentException("Cannot delete this member while unpaid dues exist.");
        }
        if (reservationService != null && reservationService.forMember(memberId).stream().anyMatch(
                r -> r.status() == model.ReservationStatus.PENDING || r.status() == model.ReservationStatus.READY)) {
            throw new IllegalArgumentException("Cannot delete this member while open reservations exist.");
        }
        members.saveAll(records);
        changeListener.onMemberChanged(MemberChangeListener.Change.DELETED, deleted);
        return deleted;
    }

    /** Gets a member by ID for an active user with member-view access. */
    public Optional<Member> getById(User actor, String memberId) throws IOException {
        requireViewPermission(actor);
        return members.findAll().stream().filter(member -> member.memberId().equals(memberId)).findFirst();
    }

    /** Lists all members sorted by name, case-insensitively. */
    public List<Member> listSorted(User actor) throws IOException {
        requireViewPermission(actor);
        return members.findAll().stream().sorted(memberOrder()).toList();
    }

    /**
     * Searches text fields and a status filter, combining both criteria with AND.
     */
    public List<Member> search(User actor, MemberSearchField field, String query, MemberStatusFilter statusFilter)
            throws IOException {
        requireViewPermission(actor);
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(statusFilter, "statusFilter");
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        String phoneQuery = normalizePhoneQuery(query);
        LocalDate today = LocalDate.now();
        return members.findAll().stream().sorted(memberOrder())
                .filter(member -> matchesStatus(member, statusFilter, today))
                .filter(member -> normalizedQuery.isEmpty()
                        || matchesQuery(member, field, normalizedQuery, phoneQuery))
                .toList();
    }

    private Member changeStatus(User actor, String memberId, MemberStatus status,
            MemberChangeListener.Change change) throws IOException {
        requireManagePermission(actor);
        List<Member> records = new ArrayList<>(members.findAll());
        int index = findIndex(records, memberId);
        if (index < 0) {
            throw new IllegalArgumentException("The selected member no longer exists.");
        }
        Member existing = records.get(index);
        Member updated = new Member(existing.memberId(), existing.name(), existing.phone(), existing.email(),
                existing.address(), existing.membershipType(), existing.joinDate(), existing.expiryDate(), status,
                existing.createdAt(), LocalDateTime.now());
        records.set(index, updated);
        members.saveAll(records);
        if (status == MemberStatus.INACTIVE && reservationService != null)
            reservationService.cancelOpenForMember(memberId);
        changeListener.onMemberChanged(change, updated);
        return updated;
    }

    private void requireViewPermission(User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.active() || !permissions.canViewMembers(actor)) {
            throw new SecurityException("An active staff or admin account is required to view members.");
        }
    }

    private void requireManagePermission(User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.active() || !permissions.canManageMembers(actor)) {
            throw new SecurityException("Only an active administrator can change members.");
        }
    }

    private Member normalize(Member draft, boolean adding) {
        Objects.requireNonNull(draft, "member");
        String name = clean(draft.name());
        String phone = normalizePhone(draft.phone());
        String email = clean(draft.email()).toLowerCase(Locale.ROOT);
        String address = clean(draft.address());
        MembershipType type = draft.membershipType();
        LocalDate joinDate = draft.joinDate() == null ? LocalDate.now() : draft.joinDate();
        LocalDate expiryDate = draft.expiryDate() == null && type != null
                ? joinDate.plusDays(settings.getInt(SettingsDefaults.key("membership.lengthDays.", type)))
                : draft.expiryDate();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Name must be 100 characters or fewer.");
        }
        if (phone.isEmpty() && email.isEmpty()) {
            throw new IllegalArgumentException("Enter at least a phone number or an email address.");
        }
        if (!phone.isEmpty() && !Pattern.matches("\\+?[0-9]{7,15}", phone)) {
            throw new IllegalArgumentException("Phone must contain 7 to 15 digits.");
        }
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (address.length() > ADDRESS_MAX_LENGTH) {
            throw new IllegalArgumentException("Address must be 300 characters or fewer.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Membership type is required.");
        }
        if (joinDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Join date cannot be in the future.");
        }
        if (expiryDate == null || expiryDate.isBefore(joinDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before the join date.");
        }
        if (!adding && draft.status() == null) {
            throw new IllegalArgumentException("Member status is required.");
        }
        return new Member(clean(draft.memberId()), name, phone, email, address, type, joinDate, expiryDate,
                adding ? MemberStatus.ACTIVE : draft.status(), draft.createdAt(), draft.updatedAt());
    }

    private static SettingsService defaultSettings() {
        try {
            return new SettingsService(new data.CsvSettingsRepository(
                    data.StoragePaths.storageDirectory().resolve("settings.txt")));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load settings.", exception);
        }
    }

    private static void ensureUniqueEmail(Member candidate, String excludedId, List<Member> records) {
        if (!candidate.email().isEmpty()) {
            records.stream().filter(member -> !member.memberId().equals(excludedId))
                    .filter(member -> member.email().equalsIgnoreCase(candidate.email()))
                    .findFirst().ifPresent(member -> {
                        throw new DuplicateMemberEmailException(member);
                    });
        }
    }

    private static void ensureUniquePhone(Member candidate, String excludedId, List<Member> records) {
        if (!candidate.phone().isEmpty()) {
            records.stream().filter(member -> !member.memberId().equals(excludedId))
                    .filter(member -> !member.phone().isEmpty() && normalizePhoneQuery(member.phone())
                            .equals(normalizePhoneQuery(candidate.phone())))
                    .findFirst().ifPresent(member -> {
                        throw new DuplicateMemberPhoneException(member);
                    });
        }
    }

    private static boolean matchesStatus(Member member, MemberStatusFilter filter, LocalDate today) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> member.status() == MemberStatus.ACTIVE;
            case SUSPENDED -> member.status() == MemberStatus.SUSPENDED;
            case INACTIVE -> member.status() == MemberStatus.INACTIVE;
            case EXPIRED -> member.expiredOn(today);
        };
    }

    private static boolean matchesQuery(Member member, MemberSearchField field, String query, String phoneQuery) {
        return switch (field) {
            case ALL -> contains(member.name(), query) || contains(member.memberId(), query)
                    || contains(member.phone(), query) || contains(member.email(), query)
                    || contains(member.membershipType().name(), query) || contains(member.status().name(), query)
                    || (!phoneQuery.isEmpty() && normalizePhoneQuery(member.phone()).contains(phoneQuery));
            case NAME -> contains(member.name(), query);
            case MEMBER_ID -> contains(member.memberId(), query);
            case PHONE -> !phoneQuery.isEmpty() && normalizePhoneQuery(member.phone()).contains(phoneQuery);
            case EMAIL -> contains(member.email(), query);
            case MEMBERSHIP_TYPE -> contains(member.membershipType().name(), query);
            case STATUS -> contains(member.status().name(), query);
        };
    }

    private static Comparator<Member> memberOrder() {
        return Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Member::memberId);
    }

    private static String generateMemberId(List<Member> records) {
        String candidate;
        boolean duplicate;
        do {
            long idBits = UUID.randomUUID().getMostSignificantBits() & 0xFFFFFFFFL;
            candidate = "MEM-" + String.format(Locale.ROOT, "%08X", idBits);
            String generated = candidate;
            duplicate = records.stream().anyMatch(member -> member.memberId().equals(generated));
        } while (duplicate);
        return candidate;
    }

    private static String normalizePhone(String value) {
        String cleaned = clean(value);
        boolean leadingPlus = cleaned.startsWith("+");
        String digits = cleaned.replaceAll("[\\s\\-()]", "").replaceFirst("^\\+", "");
        if (digits.isEmpty() && !cleaned.isEmpty()) {
            return cleaned;
        }
        return (leadingPlus ? "+" : "") + digits;
    }

    private static String normalizePhoneQuery(String value) {
        return clean(value).replaceAll("[\\s\\-()+]", "");
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static int findIndex(List<Member> records, String memberId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).memberId().equals(memberId)) {
                return index;
            }
        }
        return -1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
