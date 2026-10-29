package services;

import data.SettingsRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import model.MembershipType;
import model.User;

/** Loads, validates and publishes application settings. */
public final class SettingsService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("100000.00");
    private final SettingsRepository repository;
    private final PermissionPolicy permissions;
    private final List<SettingsChangedListener> listeners = new ArrayList<>();
    private Map<String, String> values;

    public SettingsService(SettingsRepository repository) throws IOException {
        this(repository, new PermissionPolicy());
    }

    public SettingsService(SettingsRepository repository, PermissionPolicy permissions) throws IOException {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.values = mergeDefaults(repository.load());
    }

    public BigDecimal getMoney(String key) {
        return new BigDecimal(value(key)).setScale(2, RoundingMode.HALF_UP);
    }

    public int getInt(String key) {
        return Integer.parseInt(value(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(value(key));
    }

    public <T extends Enum<T>> T getEnum(String key, Class<T> type) {
        return Enum.valueOf(type, value(key));
    }

    public String getString(String key) {
        return value(key);
    }

    public void addListener(SettingsChangedListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void save(User actor, Map<String, String> proposed) throws IOException {
        requireAdmin(actor);
        Map<String, String> next = new LinkedHashMap<>(mergeDefaults(values));
        next.putAll(proposed);
        validate(next);
        canonicalize(next);
        repository.save(next);
        values = next;
        listeners.forEach(SettingsChangedListener::settingsChanged);
    }

    public void restoreDefaults(User actor) throws IOException {
        requireAdmin(actor);
        Map<String, String> defaults = new LinkedHashMap<>(values);
        SettingsDefaults.values().forEach(defaults::put);
        repository.save(defaults);
        values = defaults;
        listeners.forEach(SettingsChangedListener::settingsChanged);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(values);
    }

    public void validate(Map<String, String> candidate) {
        for (String key : moneyKeys()) {
            money(candidate, key);
        }
        for (String key : integerKeys()) {
            positive(candidate, key);
        }
        if (integer(candidate, "loan.defaultDays") > 365 || integer(candidate, "loan.defaultDays") < 1
                || integer(candidate, "loan.maxDays") < integer(candidate, "loan.defaultDays")
                || integer(candidate, "loan.maxDays") > 365) {
            throw new IllegalArgumentException("Loan periods must be between 1 and 365 days, with maximum at least default.");
        }
        checkRange(candidate, "limit.", 1, 50);
        checkRange(candidate, "membership.lengthDays.", 1, 3650);
        checkRange(candidate, "lost.afterDays", 1, 3650);
        checkRange(candidate, "refund.windowDays", 1, 3650);
        checkRange(candidate, "reservation.holdDays", 1, 3650);
        checkRange(candidate, "reservation.maxPerMember", 1, 50);
        checkRange(candidate, "stock.lowThreshold", 0, 100000);
        checkRange(candidate, "alerts.membershipExpiryDays", 1, 90);
        if (!List.of("DAY", "WEEK", "MONTH").contains(candidate.get("charge.rent.unit"))) {
            throw new IllegalArgumentException("Rent unit must be day, week or month.");
        }
        if (Boolean.parseBoolean(candidate.get("charge.base.enabled")) && money(candidate, "charge.base.amount").signum() == 0
                || Boolean.parseBoolean(candidate.get("charge.rent.enabled")) && money(candidate, "charge.rent.amount").signum() == 0
                || Boolean.parseBoolean(candidate.get("charge.penalty.enabled")) && money(candidate, "charge.penalty.perDay").signum() == 0) {
            throw new IllegalArgumentException("An enabled charge must be greater than Rs. 0.00.");
        }
        Set<String> labels = new HashSet<>();
        for (MembershipType type : MembershipType.values()) {
            String label = candidate.get(SettingsDefaults.key("membership.label.", type));
            if (label == null || label.trim().length() < 1 || label.trim().length() > 30 || !labels.add(label.trim().toLowerCase())) {
                throw new IllegalArgumentException("Membership labels must be 1 to 30 characters and unique.");
            }
        }
    }

    private static Map<String, String> mergeDefaults(Map<String, String> loaded) {
        Map<String, String> merged = new LinkedHashMap<>(SettingsDefaults.values());
        merged.putAll(loaded);
        return merged;
    }

    private static void canonicalize(Map<String, String> values) {
        for (String key : moneyKeys()) {
            values.put(key, money(values, key).toPlainString());
        }
        for (MembershipType type : MembershipType.values()) {
            String key = SettingsDefaults.key("membership.label.", type);
            values.put(key, values.get(key).trim());
        }
    }

    private String value(String key) {
        return values.getOrDefault(key, SettingsDefaults.values().get(key));
    }

    private static BigDecimal money(Map<String, String> candidate, String key) {
        String raw = candidate.get(key);
        try {
            if (raw == null || !raw.matches("\\d+(\\.\\d{1,2})?")) {
                throw new NumberFormatException();
            }
            BigDecimal result = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (result.signum() < 0 || result.compareTo(MAX_MONEY) > 0) {
                throw new NumberFormatException();
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a non-negative amount with at most 2 decimals.");
        }
    }

    private static int integer(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(values.get(key));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + " must be a whole number.");
        }
    }

    private static void positive(Map<String, String> values, String key) {
        if (integer(values, key) < 0) {
            throw new IllegalArgumentException(key + " must not be negative.");
        }
    }

    private static void checkRange(Map<String, String> values, String prefix, int min, int max) {
        values.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(key -> {
            int number = integer(values, key);
            if (number < min || number > max) {
                throw new IllegalArgumentException(key + " is outside its allowed range.");
            }
        });
    }

    private static List<String> moneyKeys() {
        return List.of("charge.base.amount", "charge.rent.amount", "charge.penalty.perDay",
                "charge.penalty.maxPerItem", "fee.lostProcessing", "fee.damagedProcessing",
                "fee.cardReplacement", "fee.reservation", "fee.membership.registration.INDIVIDUAL",
                "fee.membership.registration.FAMILY", "fee.membership.registration.SENIOR",
                "fee.membership.registration.TEMPORARY", "fee.membership.annual.INDIVIDUAL",
                "fee.membership.annual.FAMILY", "fee.membership.annual.SENIOR", "fee.membership.annual.TEMPORARY");
    }

    private static List<String> integerKeys() {
        return List.of("loan.defaultDays", "loan.maxDays", "lost.afterDays", "refund.windowDays",
                "reservation.holdDays", "reservation.maxPerMember", "stock.lowThreshold",
                "alerts.membershipExpiryDays",
                "limit.INDIVIDUAL", "limit.FAMILY", "limit.SENIOR", "limit.TEMPORARY",
                "membership.lengthDays.INDIVIDUAL", "membership.lengthDays.FAMILY",
                "membership.lengthDays.SENIOR", "membership.lengthDays.TEMPORARY");
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.active() || !permissions.canOpenSettings(actor)) {
            throw new SecurityException("Only an active administrator can change settings.");
        }
    }
}
