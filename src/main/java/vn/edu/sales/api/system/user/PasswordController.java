package vn.edu.sales.api.system.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.sales.application.services.PasswordChangeService;

@RestController
@RequestMapping("/api/users/me/password")
public class PasswordController {
    private final PasswordChangeService service;
    public PasswordController(PasswordChangeService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(Authentication authentication, @Valid @RequestBody PasswordRequest request) {
        service.change(authentication.getName(), request.currentPassword(), request.newPassword());
    }

    public record PasswordRequest(@NotBlank String currentPassword,
                                  @NotBlank @Size(min = 12, max = 72) String newPassword) {}
}
