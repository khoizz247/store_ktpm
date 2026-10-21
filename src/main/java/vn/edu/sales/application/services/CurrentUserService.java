package vn.edu.sales.application.services;

import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.exception.ResourceNotFoundException;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import java.util.Locale;

public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) { this.users = users; }

    public User get(String email) {
        return users.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng đang hoạt động"));
    }
}
