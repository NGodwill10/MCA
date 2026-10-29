package services;

import data.ContactRepository;
import data.LoanRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import model.Contact;
import model.Loan;
import model.LoanStatus;
import model.PaymentStatus;

public final class ContactService {
    private static final int NAME_MAX_LENGTH = 100;
    private static final int NOTES_MAX_LENGTH = 500;
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");

    private final ContactRepository contacts;
    private final LoanRepository loans;

    public ContactService(ContactRepository contacts) {
        this(contacts, LoanRepository.empty());
    }

    public ContactService(ContactRepository contacts, LoanRepository loans) {
        this.contacts = Objects.requireNonNull(contacts, "contacts");
        this.loans = Objects.requireNonNull(loans, "loans");
    }

    /** Adds a contact unless a matching phone or email needs owner confirmation. */
    public Contact add(Contact draft) throws IOException {
        return add(draft, false);
    }

    /** Adds a contact, optionally accepting a duplicate phone or email. */
    public Contact add(Contact draft, boolean confirmDuplicate) throws IOException {
        Contact normalized = normalize(draft);
        List<Contact> records = new ArrayList<>(contacts.findAll());
        if (!confirmDuplicate) {
            ensureNoDuplicate(normalized, null, records);
        }
        LocalDateTime now = LocalDateTime.now();
        Contact created = new Contact(UUID.randomUUID().toString(), normalized.name(), normalized.phone(),
                normalized.email(), normalized.notes(), now, now, "");
        records.add(created);
        contacts.saveAll(records);
        return created;
    }

    /**
     * Updates editable fields while preserving the ID, creation date and reserved
     * online ID.
     */
    public Contact update(Contact draft) throws IOException {
        return update(draft, false);
    }

    /** Updates a contact, optionally accepting a matching phone or email. */
    public Contact update(Contact draft, boolean confirmDuplicate) throws IOException {
        Contact normalized = normalize(draft);
        List<Contact> records = new ArrayList<>(contacts.findAll());
        int index = findIndex(records, draft.contactId());
        if (index < 0) {
            throw new IllegalArgumentException("The selected contact no longer exists.");
        }
        Contact existing = records.get(index);
        if (!confirmDuplicate) {
            ensureNoDuplicate(normalized, existing.contactId(), records);
        }
        Contact updated = new Contact(existing.contactId(), normalized.name(), normalized.phone(),
                normalized.email(), normalized.notes(), existing.createdAt(), LocalDateTime.now(),
                existing.onlineUserId());
        records.set(index, updated);
        contacts.saveAll(records);
        return updated;
    }

    /** Deletes a contact by its account-local ID. */
    public Contact delete(String contactId) throws IOException {
        List<Contact> records = new ArrayList<>(contacts.findAll());
        int index = findIndex(records, contactId);
        if (index < 0) {
            throw new IllegalArgumentException("The selected contact no longer exists.");
        }
        List<Loan> blockingLoans = loans.findAll().stream().filter(loan -> loan.contactId().equals(contactId))
                .filter(loan -> loan.status() == LoanStatus.ACTIVE || loan.paymentStatus() == PaymentStatus.OWED)
                .toList();
        if (!blockingLoans.isEmpty()) {
            String titles = blockingLoans.stream().map(Loan::bookTitleSnapshot).distinct()
                    .reduce((left, right) -> left + ", " + right).orElse("");
            throw new IllegalArgumentException("Cannot delete this contact while a loan is active or payment is owed: "
                    + titles + ".");
        }
        Contact removed = records.remove(index);
        contacts.saveAll(records);
        return removed;
    }

    /** Finds one contact by its account-local ID. */
    public Optional<Contact> getById(String contactId) throws IOException {
        return contacts.findAll().stream().filter(contact -> contact.contactId().equals(contactId)).findFirst();
    }

    /** Lists contacts sorted by name, case-insensitively. */
    public List<Contact> listSorted() throws IOException {
        return contacts.findAll().stream()
                .sorted(Comparator.comparing(Contact::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Searches contacts by the selected field using case-insensitive contains
     * matching.
     */
    public List<Contact> search(ContactSearchField field, String query) throws IOException {
        Objects.requireNonNull(field, "field");
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        String phoneQuery = normalizePhoneQuery(query);
        return listSorted().stream()
                .filter(contact -> normalizedQuery.isEmpty() || matches(contact, field, normalizedQuery, phoneQuery))
                .toList();
    }

    private static Contact normalize(Contact draft) {
        Objects.requireNonNull(draft, "contact");
        String name = clean(draft.name());
        String phone = normalizePhone(draft.phone());
        String email = clean(draft.email()).toLowerCase(Locale.ROOT);
        String notes = clean(draft.notes());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Name must be 100 characters or fewer.");
        }
        if (!phone.isEmpty() && !Pattern.matches("\\+?[0-9]{7,15}", phone)) {
            throw new IllegalArgumentException("Phone must contain 7 to 15 digits.");
        }
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (notes.length() > NOTES_MAX_LENGTH) {
            throw new IllegalArgumentException("Notes must be 500 characters or fewer.");
        }
        return new Contact(clean(draft.contactId()), name, phone, email, notes,
                draft.createdAt(), draft.updatedAt(), clean(draft.onlineUserId()));
    }

    private static void ensureNoDuplicate(Contact candidate, String excludedId, List<Contact> records) {
        if (!candidate.phone().isEmpty()) {
            records.stream().filter(contact -> !contact.contactId().equals(excludedId))
                    .filter(contact -> !contact.phone().isEmpty() && normalizePhoneQuery(contact.phone())
                            .equals(normalizePhoneQuery(candidate.phone())))
                    .findFirst().ifPresent(contact -> {
                        throw new DuplicateContactException(contact, "phone number");
                    });
        }
        if (!candidate.email().isEmpty()) {
            records.stream().filter(contact -> !contact.contactId().equals(excludedId))
                    .filter(contact -> !contact.email().isEmpty()
                            && contact.email().equalsIgnoreCase(candidate.email()))
                    .findFirst().ifPresent(contact -> {
                        throw new DuplicateContactException(contact, "email address");
                    });
        }
    }

    private static boolean matches(Contact contact, ContactSearchField field, String query, String phoneQuery) {
        return switch (field) {
            case ALL -> contains(contact.name(), query) || contains(contact.phone(), query)
                    || contains(contact.email(), query) || contains(contact.notes(), query)
                    || (!phoneQuery.isEmpty() && normalizePhoneQuery(contact.phone()).contains(phoneQuery));
            case NAME -> contains(contact.name(), query);
            case PHONE -> !phoneQuery.isEmpty() && normalizePhoneQuery(contact.phone()).contains(phoneQuery);
            case EMAIL -> contains(contact.email(), query);
            case NOTES -> contains(contact.notes(), query);
        };
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

    private static int findIndex(List<Contact> records, String contactId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).contactId().equals(contactId)) {
                return index;
            }
        }
        return -1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}