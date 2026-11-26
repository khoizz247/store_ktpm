package vn.edu.sales.application.services;

import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

public class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;
    private final LoginAttemptLimiter loginAttempts;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, TokenProvider tokenProvider) {
        this(userRepository, passwordHasher, tokenProvider,
                new LoginAttemptLimiter(5, Duration.ofMinutes(15), Clock.systemUTC()));
    }

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, TokenProvider tokenProvider,
                       LoginAttemptLimiter loginAttempts) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
        this.loginAttempts = loginAttempts;
    }

    public AuthResult register(String fullName, String email, String rawPassword) {
        PasswordPolicy.requireStrong(rawPassword);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessConflictException("Email đã được sử dụng");
        }

        User user = userRepository.save(new User(
                null,
                normalizedEmail,
                passwordHasher.hash(rawPassword),
                fullName.trim(),
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                null
        ));
        return issue(user);
    }

    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        // Chặn trước khi kiểm tra mật khẩu, áp dụng cả email không tồn tại để không lộ tài khoản.
        loginAttempts.checkAllowed(normalizedEmail);
        User user = userRepository.findByEmail(normalizedEmail)
                .filter(found -> found.status() == UserStatus.ACTIVE)
                .filter(found -> passwordHasher.matches(rawPassword, found.passwordHash()))
                .orElse(null);
        if (user == null) {
            loginAttempts.recordFailure(normalizedEmail);
            throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác");
        }
        loginAttempts.recordSuccess(normalizedEmail);
        return issue(user);
    }

    private AuthResult issue(User user) {
        return new AuthResult(tokenProvider.generate(user.email(), user.role(), user.tokenVersion()),
                user.email(), user.role());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthResult(String accessToken, String email, Role role) {
    }
}
