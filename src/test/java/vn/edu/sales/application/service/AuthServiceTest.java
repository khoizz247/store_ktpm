package vn.edu.sales.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.services.AuthService;
import vn.edu.sales.application.services.LoginAttemptLimiter;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.exception.TooManyAttemptsException;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final String STRONG = "Test@12345678";

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final TokenProvider tokens = mock(TokenProvider.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwords, tokens,
                new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock));
    }

    @Test
    void registerNormalizesEmailAndIssuesToken() {
        when(passwords.hash(STRONG)).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User input = call.getArgument(0);
            return new User(7L, input.email(), input.passwordHash(), input.fullName(),
                    input.role(), input.status(), null);
        });
        when(tokens.generate("customer@example.com", Role.CUSTOMER, 0)).thenReturn("jwt");

        AuthService.AuthResult result = service.register("  Customer  ", " Customer@Example.COM ", STRONG);

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.role()).isEqualTo(Role.CUSTOMER);
        verify(users).existsByEmail("customer@example.com");
        verify(users).save(argThat(user -> user.role() == Role.CUSTOMER
                && user.status() == UserStatus.ACTIVE && user.fullName().equals("Customer")
                && user.passwordHash().equals("bcrypt-hash")));
    }

    @Test
    void registerRejectsWeakPasswordsBeforeTouchingStorage() {
        for (String weak : new String[]{"Test@1234", "alllowercase123!", "ALLUPPERCASE123!", "NoDigitsHere!!",
                "NoSpecial12345", "Đ".repeat(40)}) {
            assertThatThrownBy(() -> service.register("User", "weak@example.com", weak))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(users, passwords, tokens);
    }

    @Test
    void duplicateEmailDoesNotCreateAccount() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("User", " TAKEN@example.com ", STRONG))
                .isInstanceOf(BusinessConflictException.class);

        verify(users, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void loginRejectsUnknownEmailWrongPasswordAndInactiveAccount() {
        User active = new User(1L, "user@example.com", "hash", "User",
                Role.CUSTOMER, UserStatus.ACTIVE, null);
        User inactive = new User(2L, "inactive@example.com", "hash", "Inactive",
                Role.CUSTOMER, UserStatus.LOCKED, null);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(active));
        when(users.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.login("missing@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("USER@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("inactive@example.com", "right"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).generate(any(), any(), anyInt());
    }

    @Test
    void loginAcceptsNormalizedEmailAndPutsTokenVersionIntoToken() {
        User user = new User(1L, "admin@example.com", "hash", "Admin",
                Role.ADMIN, UserStatus.ACTIVE, null, 4);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Admin@123", "hash")).thenReturn(true);
        when(tokens.generate("admin@example.com", Role.ADMIN, 4)).thenReturn("jwt-admin");

        AuthService.AuthResult result = service.login(" ADMIN@example.com ", "Admin@123");

        assertThat(result.accessToken()).isEqualTo("jwt-admin");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
        verify(passwords).matches(eq("Admin@123"), eq("hash"));
    }

    @Test
    void repeatedFailuresLockEmailEvenForCorrectPasswordUntilWindowPasses() {
        User user = new User(1L, "user@example.com", "hash", "User", Role.CUSTOMER, UserStatus.ACTIVE, null);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Right@123456", "hash")).thenReturn(true);
        when(tokens.generate("user@example.com", Role.CUSTOMER, 0)).thenReturn("jwt");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login("user@example.com", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login("USER@example.com", "Right@123456"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(error -> assertThat(((TooManyAttemptsException) error).retryAfterSeconds())
                        .isBetween(1L, 900L));
        // Khi bị chặn thì không kiểm tra mật khẩu nữa (không cho dò tiếp).
        verify(passwords, times(3)).matches(any(), any());

        clock.advance(Duration.ofMinutes(15));
        assertThat(service.login("user@example.com", "Right@123456").accessToken()).isEqualTo("jwt");
    }

    @Test
    void unknownEmailsAreThrottledTheSameWayAsExistingOnes() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login("ghost@example.com", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login("ghost@example.com", "wrong"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        User user = new User(1L, "user@example.com", "hash", "User", Role.CUSTOMER, UserStatus.ACTIVE, null);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Right@123456", "hash")).thenReturn(true);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.login("user@example.com", "wrong"))
                        .isInstanceOf(InvalidCredentialsException.class);
            }
            service.login("user@example.com", "Right@123456");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override public Clock withZone(ZoneId zone) { return this; }

        @Override public Instant instant() { return now; }
    }
}
