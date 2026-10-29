package services;

import data.UserRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import model.User;

public final class AuthService {
    private static final int HASH_ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int FAILED_ATTEMPTS_BEFORE_LOCK = 3;
    private static final Duration LOCK_DURATION = Duration.ofSeconds(3);

    private final UserRepository users;
    private final SecureRandom secureRandom = new SecureRandom();
    private User signedInUser;
    private int failedAttempts;
    private Instant lockedUntil = Instant.EPOCH;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    /**
     * Creates an account and signs it in.
     * 
     * @param username     account name
     * @param displayName  optional display name
     * @param password     new password
     * @param confirmation confirmation of the new password
     * @return the created account
     * @throws IOException              if account data cannot be saved
     * @throws IllegalArgumentException if account details are invalid
     */
    public User signUp(String username, String displayName, char[] password, char[] confirmation) throws IOException {
        String normalizedUsername = username.trim();
        String normalizedDisplayName = displayName.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Username must use 1-32 letters, numbers, underscore or hyphen.");
        }
        validatePassword(password);
        if (!Arrays.equals(password, confirmation)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (users.findAll().stream().anyMatch(user -> user.username().equalsIgnoreCase(normalizedUsername))) {
            throw new IllegalArgumentException("Username already taken");
        }

        byte[] saltBytes = new byte[SALT_BYTES];
        secureRandom.nextBytes(saltBytes);
        String displayNameValue = normalizedDisplayName.isEmpty() ? normalizedUsername : normalizedDisplayName;
        User user = new User(UUID.randomUUID().toString(), normalizedUsername, displayNameValue,
                Base64.getEncoder().encodeToString(saltBytes),
                Base64.getEncoder().encodeToString(deriveHash(password, saltBytes)), LocalDateTime.now());
        users.save(user);
        signedInUser = user;
        failedAttempts = 0;
        return user;
    }

    /**
     * Verifies credentials and signs in the matching account.
     * 
     * @param username account name
     * @param password password to verify
     * @return the signed-in account, or empty when credentials are invalid or
     *         temporarily locked
     */
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
        Optional<User> match = users.findAll().stream()
                .filter(user -> user.username().equalsIgnoreCase(normalizedUsername))
                .findFirst();
        boolean valid = false;
        if (match.isPresent()) {
            User user = match.get();
            try {
                byte[] salt = Base64.getDecoder().decode(user.salt());
                byte[] expectedHash = Base64.getDecoder().decode(user.passwordHash());
                valid = MessageDigest.isEqual(expectedHash, deriveHash(password, salt));
            } catch (IllegalArgumentException exception) {
                System.getLogger(AuthService.class.getName()).log(System.Logger.Level.WARNING,
                        "Stored account credentials are malformed");
            }
        }
        if (valid) {
            failedAttempts = 0;
            signedInUser = match.orElseThrow();
            return match;
        }
        failedAttempts++;
        if (failedAttempts >= FAILED_ATTEMPTS_BEFORE_LOCK) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedAttempts = 0;
        }
        return Optional.empty();
    }

    /**
     * Verifies the current password and replaces it with a newly salted hash.
     * 
     * @param currentPassword current password
     * @param newPassword     replacement password
     * @param confirmation    confirmation of the replacement password
     * @throws IOException              if the account record cannot be updated
     * @throws IllegalArgumentException if verification or replacement validation
     *                                  fails
     * @throws IllegalStateException    if no account is signed in
     */
    public void changePassword(char[] currentPassword, char[] newPassword, char[] confirmation) throws IOException {
        if (signedInUser == null) {
            throw new IllegalStateException("Sign in before changing your password.");
        }
        if (!passwordMatches(signedInUser, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        validatePassword(newPassword);
        if (!Arrays.equals(newPassword, confirmation)) {
            throw new IllegalArgumentException("New passwords do not match.");
        }
        byte[] saltBytes = new byte[SALT_BYTES];
        secureRandom.nextBytes(saltBytes);
        User updatedUser = new User(signedInUser.userId(), signedInUser.username(), signedInUser.displayName(),
                Base64.getEncoder().encodeToString(saltBytes),
                Base64.getEncoder().encodeToString(deriveHash(newPassword, saltBytes)), signedInUser.createdAt());
        users.update(updatedUser);
        signedInUser = updatedUser;
    }

    /**
     * Returns the signed-in account when one exists.
     * 
     * @return the current account
     */
    public Optional<User> currentUser() {
        return Optional.ofNullable(signedInUser);
    }

    /**
     * Clears the signed-in account.
     */
    public void logout() {
        signedInUser = null;
    }

    /**
     * Returns the remaining authentication lockout time.
     * 
     * @return remaining lockout milliseconds, or zero
     */
    public long lockoutMillisRemaining() {
        return Math.max(0, Duration.between(Instant.now(), lockedUntil).toMillis());
    }

    private boolean passwordMatches(User user, char[] password) {
        try {
            byte[] salt = Base64.getDecoder().decode(user.salt());
            byte[] expectedHash = Base64.getDecoder().decode(user.passwordHash());
            return MessageDigest.isEqual(expectedHash, deriveHash(password, salt));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || Character.codePointCount(password, 0, password.length) < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
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