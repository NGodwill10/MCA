package services;

import data.UserRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import model.User;
import model.User.Role;

public final class AuthService {
    private static final int HASH_ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int FAILED_ATTEMPTS_BEFORE_LOCK = 3;
    private static final Duration LOCK_DURATION = Duration.ofSeconds(3);

    private final UserRepository users;
    private final SecureRandom secureRandom = new SecureRandom();
    private int failedAttempts;
    private Instant lockedUntil = Instant.EPOCH;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public boolean hasAccounts() {
        return !users.findAll().isEmpty();
    }

    public User createInitialAdmin(String username, String displayName, char[] password) throws IOException {
        String normalizedUsername = username.trim();
        String normalizedName = displayName.trim();
        if (normalizedUsername.isEmpty() || normalizedName.isEmpty() || password == null || password.length < 8) {
            throw new IllegalArgumentException("Enter a username, display name and a password of at least 8 characters.");
        }
        if (hasAccounts()) {
            throw new IllegalStateException("An administrator account already exists.");
        }
        byte[] saltBytes = new byte[SALT_BYTES];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hash = hashPassword(password, saltBytes);
        User admin = new User(UUID.randomUUID().toString(), normalizedUsername, normalizedName, Role.ADMIN,
                salt, hash, true, LocalDateTime.now());
        users.save(admin);
        return admin;
    }

    public Optional<User> authenticate(String username, char[] password) {
        Instant now = Instant.now();
        if (now.isBefore(lockedUntil)) {
            return Optional.empty();
        }
        if (!lockedUntil.equals(Instant.EPOCH)) {
            failedAttempts = 0;
            lockedUntil = Instant.EPOCH;
        }

        String normalizedUsername = username.trim();
        List<User> accounts = users.findAll();
        Optional<User> match = accounts.stream()
                .filter(user -> user.username().equalsIgnoreCase(normalizedUsername))
                .findFirst();
        boolean valid = false;
        if (match.isPresent() && match.get().active()) {
            User user = match.get();
            byte[] salt = Base64.getDecoder().decode(user.salt());
            byte[] expectedHash = Base64.getDecoder().decode(user.passwordHash());
            byte[] actualHash = deriveHash(password, salt);
            valid = MessageDigest.isEqual(expectedHash, actualHash);
        }
        if (valid) {
            failedAttempts = 0;
            return match;
        }

        failedAttempts++;
        if (failedAttempts >= FAILED_ATTEMPTS_BEFORE_LOCK) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedAttempts = 0;
        }
        return Optional.empty();
    }

    public long lockoutMillisRemaining() {
        return Math.max(0, Duration.between(Instant.now(), lockedUntil).toMillis());
    }

    private String hashPassword(char[] password, byte[] salt) {
        return Base64.getEncoder().encodeToString(deriveHash(password, salt));
    }

    private byte[] deriveHash(char[] password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }
}
