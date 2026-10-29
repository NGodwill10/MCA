package services;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsDefaults {
    // ASSUMPTION: Charges start disabled, so lending remains free until configured.
    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("schemaVersion", "1"),
            Map.entry("pricing.base.enabled", "false"),
            Map.entry("pricing.base.amount", "0.00"),
            Map.entry("pricing.rent.enabled", "false"),
            Map.entry("pricing.rent.unit", "DAY"),
            Map.entry("pricing.rent.amount", "0.00"),
            Map.entry("pricing.penalty.enabled", "false"),
            Map.entry("pricing.penalty.perDay", "0.00"),
            Map.entry("pricing.penalty.maxPerBook", "0.00"),
            Map.entry("loan.defaultDays", "14"),
            Map.entry("alerts.enabled", "true"),
            Map.entry("alerts.dueSoonDays", "3"));

    private SettingsDefaults() {
    }

    public static Map<String, String> values() {
        return Map.copyOf(VALUES);
    }

    static Map<String, String> mutableValues() {
        return new LinkedHashMap<>(VALUES);
    }
}