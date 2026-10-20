package vn.edu.sales.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.TransactionRunner;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.application.services.PasswordChangeService;
import vn.edu.sales.domain.exception.InvalidCredentialsException;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {
    private UserRepository users;
    private PasswordHasher passwords;
    private PasswordChangeService service;
    private User activeUser;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordHasher.class);

        TransactionRunner transactions = new TransactionRunner() {
            @Override
            public <T> T execute(Supplier<T> work) {
                return work.get();
            }
        };

        service = new PasswordChangeService(
                users, passwords, transactions
        );

        activeUser = new User(
                7L,
                "customer@example.test",
                "old-hash",
                "Khách test",
                Role.CUSTOMER,
                UserStatus.ACTIVE,
                null
        );

        lenient().when(users.findByEmail("customer@example.test"))
                .thenReturn(Optional.of(activeUser));
    }

    @Test
    void correctCurrentPasswordUpdatesPasswordWithNewHash() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);
        when(passwords.matches("NewPassword@456", "old-hash"))
                .thenReturn(false);
        when(passwords.hash("NewPassword@456"))
                .thenReturn("new-hash");

        service.change(
                "customer@example.test",
                "OldPassword@123",
                "NewPassword@456"
        );

        verify(passwords).hash("NewPassword@456");
        verify(users).updatePassword(7L, "new-hash");
    }

    @Test
    void wrongCurrentPasswordDoesNotUpdatePassword() {
        when(passwords.matches("WrongPassword@123", "old-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "WrongPassword@123",
                "NewPassword@456"
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void weakNewPasswordDoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "OldPassword@123",
                "short"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void accountNotFoundDoesNotUpdatePassword() {
        String email = "missing@example.test";
        when(users.findByEmail(email)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.change(
                email,
                "OldPassword@123",
                "NewPassword@456"
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(users).findByEmail(email);
        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void lockedAccountDoesNotUpdatePassword() {
        User lockedUser = new User(
                8L,
                "locked@example.test",
                "locked-hash",
                "Tài khoản bị khoá",
                Role.CUSTOMER,
                UserStatus.LOCKED,
                null
        );
        String email = "locked@example.test";
        when(users.findByEmail(email)).thenReturn(Optional.of(lockedUser));
        when(passwords.matches("OldPassword@123", "locked-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.change(
                email,
                "OldPassword@123",
                "NewPassword@456"
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(users).findByEmail(email);
        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void newPasswordSameAsOldDoesNotUpdatePassword() {
        when(passwords.matches("SamePassword@123", "old-hash"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "SamePassword@123",
                "SamePassword@123"
        )).isInstanceOf(IllegalArgumentException.class);
        verify(users).findByEmail("customer@example.test");
        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void newPasswordMissingUppercaseDoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "OldPassword@123",
                "newpassword@123"
        )).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void newPasswordMissingLowercaseDoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "OldPassword@123",
                "NEWPASSWORD@123"
        )).isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());

    }

    @Test
    void newPasswordMissingDigitDoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "OldPassword@123",
                "NewPassword@@@"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void newPasswordMissingSpecialCharacterDoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
                .thenReturn(true);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
                "OldPassword@123",
                "NewPassword123"
        )).isInstanceOf(IllegalArgumentException.class);

        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void newPasswordExceeds72BytesUtf8DoesNotUpdatePassword() {
        when(passwords.matches("OldPassword@123", "old-hash"))
            .thenReturn(true);
        StringBuilder sb = new StringBuilder(80);
        sb.append("Ab1@");
        for (int i = 0; i < 76; i++) {
            sb.append('a');
        }
        String tooLong = sb.toString();
        int bytes = tooLong.getBytes(StandardCharsets.UTF_8).length;
        org.assertj.core.api.Assertions.assertThat(bytes).isGreaterThan(72);
        assertThatThrownBy(() -> service.change(
                "customer@example.test",
        "OldPassword@123",
                tooLong
             )).isInstanceOf(IllegalArgumentException.class);

        verify(users, never()).updatePassword(anyLong(), anyString());
        verify(passwords, never()).hash(anyString());
    }

    @Test
    void uppercaseEmailIsNormalizedWhenFindingAccount() {
            String upperEmail = "CUSTOMER@EXAMPLE.TEST";
        String normalizedEmail = upperEmail.toLowerCase(Locale.ROOT);
        reset(users);
        when(users.findByEmail(normalizedEmail)).thenReturn(Optional.of(activeUser));
        when(passwords.matches("OldPassword@123", "old-hash")).thenReturn(true);
        when(passwords.matches("NewPassword@456", "old-hash")).thenReturn(false);
        when(passwords.hash("NewPassword@456")).thenReturn("new-hash");
        service.change(
                upperEmail,
                "OldPassword@123",
                "NewPassword@456"
        );

        verify(users).findByEmail(normalizedEmail);
        verify(passwords).hash("NewPassword@456");
        verify(users).updatePassword(7L, "new-hash");
    }
}