package data;

import java.io.IOException;
import java.util.Map;

public interface SettingsRepository {
    Map<String, String> load();

    void save(Map<String, String> values) throws IOException;
}