import data.CsvSettingsRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import services.SettingsService;
import ui.PricingForm;

public final class PricingFormInitializationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path root = Files.createTempDirectory("libgrampocket-pricing-form-");
        try {
            SettingsService settings = new SettingsService(new CsvSettingsRepository(root.resolve("settings.txt")));
            SwingUtilities.invokeAndWait(() -> new PricingForm(settings, settings.pricingDefaults()));
            System.out.println("PricingFormInitializationTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}