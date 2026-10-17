package vn.edu.sales.application.services;

import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.model.UserStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class PasswordChangeService {
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final TransactionRunner transactions;

    public PasswordChangeService(UserRepository users, PasswordHasher passwords, TransactionRunner transactions) {
        this.users = users; this.passwords = passwords; this.transactions = transactions;
    }

    public void change(String email, String currentPassword, String newPassword) {
        transactions.execute(() -> {
            var user = users.findByEmail(email.toLowerCase(Locale.ROOT))
                    .filter(account -> account.status() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidCredentialsException("Tài khoản không còn hoạt động"));
            if (!passwords.matches(currentPassword, user.passwordHash()))
                throw new InvalidCredentialsException("Mật khẩu hiện tại không chính xác");
            if (newPassword == null || newPassword.length() < 12
                    || newPassword.getBytes(StandardCharsets.UTF_8).length > 72
                    || !newPassword.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*"))
                throw new IllegalArgumentException("Mật khẩu mới cần 12-72 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt");
            if (passwords.matches(newPassword, user.passwordHash()))
                throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
            users.updatePassword(user.id(), passwords.hash(newPassword));
            return null;
        });
    }
}
