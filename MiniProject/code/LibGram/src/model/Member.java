package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Member(
        String memberId,
        String name,
        String phone,
        String email,
        String address,
        MembershipType membershipType,
        LocalDate joinDate,
        LocalDate expiryDate,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public boolean expiredOn(LocalDate date) {
        return expiryDate.isBefore(date);
    }
}