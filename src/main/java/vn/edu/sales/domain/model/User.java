package vn.edu.sales.domain.model;

import java.time.LocalDateTime;

public record User (
        Long id,
        String email,
        String passwordHash,
        String fullName,
        Role role,
        UserStatus status,
        LocalDateTime createdAt
) {
    public User {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email không được để trống.");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Mật khẩu không hợp lệ.");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Họ tên không được để trống.");
        if (role == null) role = Role.CUSTOMER;
        if (status == null) status = UserStatus.ACTIVE;
    }
}
