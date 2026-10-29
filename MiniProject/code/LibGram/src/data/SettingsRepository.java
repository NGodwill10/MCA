package data;

import java.io.IOException;
import java.util.Map;

/** Stores the editable application settings. */
public interface SettingsRepository {
    Map<String, String> load() throws IOException;

    void save(Map<String, String> values) throws IOException;
}
