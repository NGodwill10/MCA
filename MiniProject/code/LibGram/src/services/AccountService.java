package services;

import data.UserRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import model.User;
import model.User.Role;

/** Administrator-only staff account management. */
public final class AccountService {
    private static final int HASH_ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final UserRepository users;
    private final PermissionPolicy permissions;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository users) {
        this(users, new PermissionPolicy());
    }

    public AccountService(UserRepository users, PermissionPolicy permissions) {
        this.users = Objects.requireNonNull(users, "users");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public List<User> list(User actor) {
        requireAdmin(actor);
        return users.findAll();
    }

    public User createStaff(User actor, String username, String displayName, char[] password) throws IOException {
        requireAdmin(actor);
        String normalizedUsername = clean(username);
        String normalizedName = clean(displayName);
        if (normalizedUsername.isEmpty() || normalizedName.isEmpty() || password == null || password.length < 8) {
            throw new IllegalArgumentException("Enter a username, display name and a temporary password of at least 8 characters.");
        }
        if (users.findAll().stream().anyMatch(user -> user.username().equalsIgnoreCase(normalizedUsername))) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        User created = new User(UUID.randomUUID().toString(), normalizedUsername, normalizedName, Role.STAFF,
                encodedSalt(), "", true, LocalDateTime.now());
        created = withPassword(created, password);
        users.save(created);
        return created;
    }

    public User resetPassword(User actor, String userId, char[] password) throws IOException {
        requireAdmin(actor);
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Enter a new temporary password of at least 8 characters.");
        }
        User existing = find(userId);
        return save(withPassword(existing, password));
    }

    public User setActive(User actor, String userId, boolean active) throws IOException {
        requireAdmin(actor);
        User existing = find(userId);
        if (!active && existing.role() == Role.ADMIN && existing.active()
                && users.findAll().stream().filter(user -> user.role() == Role.ADMIN && user.active()).count() <= 1) {
            throw new IllegalArgumentException("The last active administrator cannot be disabled.");
        }
        return save(new User(existing.userId(), existing.username(), existing.displayName(), existing.role(),
                existing.salt(), existing.passwordHash(), active, existing.createdAt()));
    }

    private User save(User user) throws IOException {
        users.save(user);
        return user;
    }

    private User find(String userId) {
        return users.findAll().stream().filter(user -> user.userId().equals(userId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The selected account no longer exists."));
    }

    private User withPassword(User user, char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new User(user.userId(), user.username(), user.displayName(), user.role(), Base64.getEncoder().encodeToString(salt),
                hash(password, salt), user.active(), user.createdAt());
    }

    private String encodedSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hash(char[] password, byte[] salt) {
        PBEKeySpec key = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_BITS);
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(key).getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            key.clearPassword();
        }
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.active() || !permissions.canManageAccounts(actor)) {
            throw new SecurityException("Only an active administrator can manage accounts.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
