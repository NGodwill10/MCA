import data.CsvSettingsRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import model.PricingTerms;
import services.SettingsDefaults;
import services.SettingsService;

public final class SettingsServiceTest {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("pocket-settings-test");
        SettingsService first = service(root.resolve("users/alice/settings.txt"));
        check(first.allValues().entrySet().containsAll(SettingsDefaults.values().entrySet()));
        check(first.getInt("loan.defaultDays") == 14);
        check(first.getBoolean("alerts.enabled"));
        check(first.getMoney("pricing.base.amount").toPlainString().equals("0.00"));
        check(first.getEnum("pricing.rent.unit", PricingTerms.RentUnit.class) == PricingTerms.RentUnit.DAY);
        check(first.pricingFor("lend").equals(first.pricingDefaults()));
        first.saveLastPricing("lend", new PricingTerms(true, new java.math.BigDecimal("5.00"), false,
                PricingTerms.RentUnit.DAY, java.math.BigDecimal.ZERO, false, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO));
        check(first.pricingFor("lend").baseAmount().toPlainString().equals("5.00"));
        check(service(root.resolve("users/alice/settings.txt")).pricingFor("lend").baseAmount()
                .toPlainString().equals("5.00"));
        check(first.pricingFor("borrow").equals(first.pricingDefaults()));

        int[] notifications = { 0 };
        first.addListener(() -> notifications[0]++);
        first.save(Map.of("loan.defaultDays", "21", "custom.preference", "kept"));
        check(notifications[0] == 1);
        check(Files.exists(root.resolve("users/alice/settings.txt.bak")));
        check(service(root.resolve("users/alice/settings.txt")).getString("custom.preference").equals("kept"));

        Path beforeFailure = root.resolve("users/alice/settings.txt");
        String saved = Files.readString(beforeFailure);
        expectInvalid(() -> first.save(Map.of("pricing.rent.enabled", "true", "pricing.rent.amount", "0")));
        expectInvalid(() -> first.save(Map.of("pricing.base.enabled", "true", "pricing.base.amount", "0.00")));
        expectInvalid(() -> first.save(Map.of("pricing.penalty.enabled", "true",
                "pricing.penalty.perDay", "0.00")));
        check(Files.readString(beforeFailure).equals(saved));
        expectInvalid(() -> first.save(Map.of("pricing.base.amount", "1.001")));
        expectInvalid(() -> first.save(Map.of("pricing.base.amount", "100000.01")));
        expectInvalid(() -> first.save(Map.of("pricing.base.amount", "-1.00")));
        expectInvalid(() -> first.save(Map.of("loan.defaultDays", "366")));
        expectInvalid(() -> first.save(Map.of("alerts.dueSoonDays", "0")));
        expectInvalid(() -> first.save(Map.of("alerts.dueSoonDays", "31")));
        expectInvalid(() -> first.save(Map.of("alerts.enabled", "sometimes")));
        expectInvalid(() -> first.save(Map.of("pricing.rent.unit", "FORTNIGHT")));
        first.save(Map.of("pricing.penalty.maxPerBook", "0.00"));

        SettingsService second = service(root.resolve("users/bob/settings.txt"));
        check(second.getInt("loan.defaultDays") == 14);
        check(second.getString("custom.preference").isEmpty());
        first.restoreDefaults();
        check(first.getInt("loan.defaultDays") == 14);
        check(first.getString("custom.preference").equals("kept"));
        System.out.println("SettingsServiceTest passed");
    }

    private static SettingsService service(Path file) throws IOException {
        return new SettingsService(new CsvSettingsRepository(file));
    }

    private static void expectInvalid(CheckedAction action) throws IOException {
        try {
            action.run();
            throw new AssertionError("Expected settings validation failure");
        } catch (IllegalArgumentException expected) {
            // Expected invalid setting.
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Settings assertion failed");
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws IOException;
    }
}