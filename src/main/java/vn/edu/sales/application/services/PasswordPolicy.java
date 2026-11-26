package vn.edu.sales.application.services;

import java.nio.charset.StandardCharsets;

/**
 * Một chính sách mật khẩu duy nhất cho đăng ký và đổi mật khẩu.
 * Giới hạn 72 byte UTF-8 là giới hạn đầu vào của BCrypt.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;
    public static final String REQUIREMENT =
            "Mật khẩu cần 12-72 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt";

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && password.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*");
    }

    public static void requireStrong(String password) {
        if (!isStrong(password)) throw new IllegalArgumentException(REQUIREMENT);
    }
}
