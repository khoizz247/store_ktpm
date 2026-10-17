package vn.edu.sales.api.system.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.ResourceNotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class CurrentUserController {
    private final UserRepository users;

    public CurrentUserController(UserRepository users) { this.users = users; }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .toList();
        var user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return new CurrentUserResponse(user.id(), user.email(), user.fullName(), roles);
    }

    public record CurrentUserResponse(Long id, String email, String fullName, List<String> roles) {
    }
}