package vn.edu.sales.api.system.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.sales.application.services.CurrentUserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class CurrentUserController {
    private final CurrentUserService users;

    public CurrentUserController(CurrentUserService users) { this.users = users; }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .toList();
        var user = users.get(authentication.getName());
        return new CurrentUserResponse(user.id(), user.email(), user.fullName(), roles);
    }

    public record CurrentUserResponse(Long id, String email, String fullName, List<String> roles) {
    }
}
