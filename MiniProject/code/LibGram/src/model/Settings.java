package model;

import java.util.Map;

/** Immutable value object representing a settings snapshot. */
public record Settings(Map<String, String> values) {
    public Settings {
        values = Map.copyOf(values);
    }
}
