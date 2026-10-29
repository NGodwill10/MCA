package data;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locates app-local data independently of the process working directory. */
public final class StoragePaths {
    private StoragePaths() {
    }

    /**
     * Returns storage beside the app's compiled output directory (out/ or bin/).
     * For a packaged JAR, storage is beside the JAR inside the app folder.
     * 
     * @throws IOException if the application location cannot be determined
     */
    public static Path storageDirectory() throws IOException {
        return storageDirectory(new String[0]);
    }

    /**
     * Resolves storage beside the application or from a development-only --storage
     * override.
     * 
     * @param arguments application command-line arguments
     * @return the normalized storage directory
     * @throws IOException              if the application location cannot be
     *                                  determined
     * @throws IllegalArgumentException if the override argument is malformed
     */
    public static Path storageDirectory(String... arguments) throws IOException {
        Path appDirectory = appDirectory();
        Path override = storageOverride(arguments);
        return (override == null ? appDirectory.resolve("storage")
                : override.isAbsolute() ? override : appDirectory.resolve(override)).toAbsolutePath().normalize();
    }

    private static Path appDirectory() throws IOException {
        var codeSource = StoragePaths.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new IOException("Cannot determine the application folder.");
        }
        try {
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            Path appDirectory = location.getParent();
            if (appDirectory == null || (!Files.isDirectory(location) && !Files.isRegularFile(location))) {
                throw new IOException("Cannot determine the application folder from " + location);
            }
            return appDirectory;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("Cannot determine the application folder.", exception);
        }
    }

    private static Path storageOverride(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }
        if (arguments.length != 2 || !"--storage".equals(arguments[0]) || arguments[1].isBlank()) {
            throw new IllegalArgumentException("Usage: java -cp out Main [--storage <folder>]");
        }
        return Path.of(arguments[1]);
    }
}
