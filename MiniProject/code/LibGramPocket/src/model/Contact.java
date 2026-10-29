package model;

import java.time.LocalDateTime;

public record Contact(
        String contactId,
        String name,
        String phone,
        String email,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String onlineUserId) {
}