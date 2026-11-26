package vn.edu.sales.domain.model;

import java.time.LocalDateTime;

/**
 * {@code tokenVersion} tăng mỗi khi cần vô hiệu hóa mọi JWT đã cấp (ví dụ đổi mật khẩu).
 */
public record User (
        Long id,
        String email,
        String passwordHash,
        String fullName,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        int tokenVersion
) {
    public User {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email không được để trống.");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Mật khẩu không hợp lệ.");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Họ tên không được để trống.");
        if (role == null) role = Role.CUSTOMER;
        if (status == null) status = UserStatus.ACTIVE;
        if (tokenVersion < 0) throw new IllegalArgumentException("Phiên bản token không hợp lệ.");
    }

    public User(Long id, String email, String passwordHash, String fullName,
                Role role, UserStatus status, LocalDateTime createdAt) {
        this(id, email, passwordHash, fullName, role, status, createdAt, 0);
    }
}
