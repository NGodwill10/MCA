package services;

@FunctionalInterface
public interface SettingsChangedListener {
    void settingsChanged();
}