package model;

import java.time.LocalDateTime;

public record User(
        String userId,
        String username,
        String displayName,
        Role role,
        String salt,
        String passwordHash,
        boolean active,
        LocalDateTime createdAt) {
    public enum Role {
        ADMIN,
        STAFF
    }
}