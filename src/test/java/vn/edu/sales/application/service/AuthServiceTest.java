package vn.edu.sales.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TokenProvider;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.services.AuthService;
import vn.edu.sales.domain.exception.BusinessConflictException;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final TokenProvider tokens = mock(TokenProvider.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwords, tokens);
    }

    @Test
    void registerNormalizesEmailAndIssuesToken() {
        when(passwords.hash("Test@1234")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User input = call.getArgument(0);
            return new User(7L, input.email(), input.passwordHash(), input.fullName(),
                    input.role(), input.status(), null);
        });
        when(tokens.generate("customer@example.com", Role.CUSTOMER)).thenReturn("jwt");

        AuthService.AuthResult result = service.register("  Customer  ", " Customer@Example.COM ", "Test@1234");

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.role()).isEqualTo(Role.CUSTOMER);
        verify(users).existsByEmail("customer@example.com");
        verify(users).save(argThat(user -> user.role() == Role.CUSTOMER
                && user.status() == UserStatus.ACTIVE && user.fullName().equals("Customer")
                && user.passwordHash().equals("bcrypt-hash")));
    }

    @Test
    void duplicateEmailDoesNotCreateAccount() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("User", " TAKEN@example.com ", "Test@1234"))
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
        verify(tokens, never()).generate(any(), any());
    }

    @Test
    void loginAcceptsNormalizedEmailAndCorrectPassword() {
        User user = new User(1L, "admin@example.com", "hash", "Admin",
                Role.ADMIN, UserStatus.ACTIVE, null);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Admin@123", "hash")).thenReturn(true);
        when(tokens.generate("admin@example.com", Role.ADMIN)).thenReturn("jwt-admin");

        AuthService.AuthResult result = service.login(" ADMIN@example.com ", "Admin@123");

        assertThat(result.accessToken()).isEqualTo("jwt-admin");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
        verify(passwords).matches(eq("Admin@123"), eq("hash"));
    }
}
