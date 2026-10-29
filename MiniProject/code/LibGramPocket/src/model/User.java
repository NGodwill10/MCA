package model;

import java.time.LocalDateTime;

public record User(
        String userId,
        String username,
        String displayName,
        String salt,
        String passwordHash,
        LocalDateTime createdAt) {
}