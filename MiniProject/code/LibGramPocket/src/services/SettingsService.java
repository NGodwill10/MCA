package services;

import data.SettingsRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import model.PricingTerms;
import model.PricingTerms.RentUnit;

public final class SettingsService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("100000.00");
    private final SettingsRepository repository;
    private final List<SettingsChangedListener> listeners = new ArrayList<>();
    private Map<String, String> values;

    public SettingsService(SettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        values = new LinkedHashMap<>(SettingsDefaults.values());
        values.putAll(repository.load());
    }

    public BigDecimal getMoney(String key) {
        String value = getString(key);
        try {
            return parseMoney(value);
        } catch (IllegalArgumentException exception) {
            return new BigDecimal(SettingsDefaults.values().getOrDefault(key, "0.00"));
        }
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(getString(key));
        } catch (NumberFormatException exception) {
            return Integer.parseInt(SettingsDefaults.values().getOrDefault(key, "0"));
        }
    }

    public boolean getBoolean(String key) {
        String value = getString(key);
        return value.equalsIgnoreCase("true") || (!value.equalsIgnoreCase("false")
                && Boolean.parseBoolean(SettingsDefaults.values().getOrDefault(key, "false")));
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> enumType) {
        String fallback = SettingsDefaults.values().getOrDefault(key, enumType.getEnumConstants()[0].name());
        try {
            return Enum.valueOf(enumType, getString(key).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Enum.valueOf(enumType, fallback.toUpperCase(Locale.ROOT));
        }
    }

    public String getString(String key) {
        return values.getOrDefault(key, SettingsDefaults.values().getOrDefault(key, ""));
    }

    public Map<String, String> allValues() {
        return Map.copyOf(values);
    }

    public PricingTerms pricingDefaults() {
        return pricingTerms("pricing.");
    }

    public PricingTerms pricingFor(String direction) {
        // ASSUMPTION: Each direction reuses its own last-confirmed pricing before
        // defaults.
        String prefix = lastPricingPrefix(direction);
        if (!values.containsKey(prefix + "base.enabled")) {
            return pricingDefaults();
        }
        return pricingTerms(prefix);
    }

    public void saveLastPricing(String direction, PricingTerms terms) throws IOException {
        String prefix = lastPricingPrefix(direction);
        save(Map.of(prefix + "base.enabled", Boolean.toString(terms.baseEnabled()),
                prefix + "base.amount", terms.baseAmount().toPlainString(),
                prefix + "rent.enabled", Boolean.toString(terms.rentEnabled()),
                prefix + "rent.unit", terms.rentUnit().name(),
                prefix + "rent.amount", terms.rentAmount().toPlainString(),
                prefix + "penalty.enabled", Boolean.toString(terms.penaltyEnabled()),
                prefix + "penalty.perDay", terms.penaltyPerDay().toPlainString(),
                prefix + "penalty.maxPerBook", terms.penaltyMaxPerBook().toPlainString()));
    }

    public void save(Map<String, String> changes) throws IOException {
        Map<String, String> updated = new LinkedHashMap<>(values);
        updated.putAll(changes);
        validate(updated);
        repository.save(updated);
        values = updated;
        listeners.forEach(SettingsChangedListener::settingsChanged);
    }

    public void restoreDefaults() throws IOException {
        save(SettingsDefaults.values());
    }

    public void addListener(SettingsChangedListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(SettingsChangedListener listener) {
        listeners.remove(listener);
    }

    private PricingTerms pricingTerms(String prefix) {
        return new PricingTerms(getBoolean(prefix + "base.enabled"), getMoney(prefix + "base.amount"),
                getBoolean(prefix + "rent.enabled"), getEnum(prefix + "rent.unit", RentUnit.class),
                getMoney(prefix + "rent.amount"), getBoolean(prefix + "penalty.enabled"),
                getMoney(prefix + "penalty.perDay"), getMoney(prefix + "penalty.maxPerBook"));
    }

    private static String lastPricingPrefix(String direction) {
        String normalized = Objects.requireNonNull(direction, "direction").toLowerCase(Locale.ROOT);
        if (!normalized.equals("lend") && !normalized.equals("borrow")) {
            throw new IllegalArgumentException("Direction must be lend or borrow.");
        }
        return "last." + normalized + ".";
    }

    private static void validate(Map<String, String> settings) {
        validateBoolean(settings, "pricing.base.enabled");
        validateBoolean(settings, "pricing.rent.enabled");
        validateBoolean(settings, "pricing.penalty.enabled");
        validateMoney(settings, "pricing.base.amount");
        validateMoney(settings, "pricing.rent.amount");
        validateMoney(settings, "pricing.penalty.perDay");
        validateMoney(settings, "pricing.penalty.maxPerBook");
        validateEnabledAmount(settings, "pricing.base.enabled", "pricing.base.amount", "Base charge");
        validateEnabledAmount(settings, "pricing.rent.enabled", "pricing.rent.amount", "Rent");
        validateEnabledAmount(settings, "pricing.penalty.enabled", "pricing.penalty.perDay", "Overdue penalty");
        validateEnum(settings.get("pricing.rent.unit"), RentUnit.class, "Rent unit");
        validateInteger(settings, "loan.defaultDays", 1, 365);
        validateBoolean(settings, "alerts.enabled");
        validateInteger(settings, "alerts.dueSoonDays", 1, 30);

        for (String prefix : List.of("last.lend.", "last.borrow.")) {
            if (settings.containsKey(prefix + "base.enabled")) {
                validateBoolean(settings, prefix + "base.enabled");
                validateBoolean(settings, prefix + "rent.enabled");
                validateBoolean(settings, prefix + "penalty.enabled");
                validateMoney(settings, prefix + "base.amount");
                validateMoney(settings, prefix + "rent.amount");
                validateMoney(settings, prefix + "penalty.perDay");
                validateMoney(settings, prefix + "penalty.maxPerBook");
                validateEnabledAmount(settings, prefix + "base.enabled", prefix + "base.amount", "Base charge");
                validateEnabledAmount(settings, prefix + "rent.enabled", prefix + "rent.amount", "Rent");
                validateEnabledAmount(settings, prefix + "penalty.enabled", prefix + "penalty.perDay",
                        "Overdue penalty");
                validateEnum(settings.get(prefix + "rent.unit"), RentUnit.class, "Rent unit");
            }
        }
        validateMenu(settings.get("ui.lastMenu"));
        validateSort(settings);
    }

    private static void validateMoney(Map<String, String> settings, String key) {
        if (settings.containsKey(key)) {
            parseMoney(settings.get(key));
        }
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Money must be a number with at most 2 decimals.");
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException("Money must be at most Rs. 100000.00.");
        }
        return amount.setScale(2);
    }

    private static void validateEnabledAmount(Map<String, String> settings, String enabledKey, String amountKey,
            String label) {
        if (Boolean.parseBoolean(settings.get(enabledKey))
                && new BigDecimal(settings.getOrDefault(amountKey, "0.00")).signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero when enabled.");
        }
    }

    private static void validateBoolean(Map<String, String> settings, String key) {
        if (settings.containsKey(key) && !settings.get(key).equalsIgnoreCase("true")
                && !settings.get(key).equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Enter true or false for " + key + ".");
        }
    }

    private static void validateInteger(Map<String, String> settings, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(settings.getOrDefault(key, ""));
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(key + " must be from " + minimum + " to " + maximum + ".");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a whole number.", exception);
        }
    }

    private static <E extends Enum<E>> void validateEnum(String value, Class<E> type, String label) {
        try {
            Enum.valueOf(type, Objects.requireNonNull(value, label).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Choose a valid " + label.toLowerCase(Locale.ROOT) + ".", exception);
        }
    }

    private static void validateMenu(String value) {
        if (value != null && !List.of("BOOKKEEPING", "LENDING", "BORROWING").contains(value)) {
            throw new IllegalArgumentException("Choose a valid last menu.");
        }
    }

    private static void validateSort(Map<String, String> settings) {
        String column = settings.get("ui.books.sortColumn");
        String direction = settings.get("ui.books.sortDirection");
        if (column != null) {
            try {
                int index = Integer.parseInt(column);
                if (index < 0 || index > 8) {
                    throw new IllegalArgumentException("Choose a valid catalog sort column.");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Catalog sort column must be a whole number.", exception);
            }
        }
        if (direction != null && !List.of("ASCENDING", "DESCENDING").contains(direction)) {
            throw new IllegalArgumentException("Choose a valid catalog sort direction.");
        }
    }
}