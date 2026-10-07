package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long count();

    void updatePassword(Long id, String passwordHash);
}
