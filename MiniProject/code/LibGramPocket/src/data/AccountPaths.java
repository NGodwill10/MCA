package data;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountPaths {
    // ASSUMPTION: Per-account folder names are limited to 1-32 ASCII letters,
    // digits, underscores or hyphens.
    private static final Pattern SAFE_USERNAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private AccountPaths() {
    }

    public static Path forUsername(Path usersDirectory, String username) {
        if (!SAFE_USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must use letters, numbers, underscore or hyphen.");
        }
        return usersDirectory.resolve(username.toLowerCase(Locale.ROOT)).normalize();
    }
}