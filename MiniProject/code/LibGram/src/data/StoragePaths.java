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
     * @throws IOException if the application location cannot be determined
     */
    public static Path storageDirectory() throws IOException {
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
            return appDirectory.resolve("storage");
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("Cannot determine the application folder.", exception);
        }
    }

    public static Path storageDirectory(Path requested) throws IOException {
        if (requested == null) return storageDirectory();
        Path result = requested.toAbsolutePath().normalize();
        Files.createDirectories(result);
        return result;
    }
}
