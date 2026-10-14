package vn.edu.sales.infrastructure.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
@Profile("mysql")
public class BootstrapAdminConfig {
    @Bean
    CommandLineRunner bootstrapAdmin(UserRepository users, PasswordHasher passwords,
                                     @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                                     @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.isBlank())
                throw new IllegalStateException("Cần đặt cả BOOTSTRAP_ADMIN_EMAIL và BOOTSTRAP_ADMIN_PASSWORD");
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72
                    || !password.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*"))
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD cần 12-72 ký tự đủ độ mạnh");
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
                throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL không hợp lệ");
            if (users.findByEmail(normalized).isPresent()) return;
            // Bootstrap is intentionally unavailable once any account exists.
            if (users.count() != 0) return;
            users.save(new User(null, normalized, passwords.hash(password), "Quản trị viên đầu tiên",
                    Role.ADMIN, UserStatus.ACTIVE, null));
        };
    }
}
