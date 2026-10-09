package vn.edu.sales.infrastructure.persistence.user;

import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.User;

import java.util.Optional;

public class UserRepositoryAdapter implements UserRepository {
    private final SpringDataUserRepository repository;

    public UserRepositoryAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        return toDomain(repository.save(new UserJpaEntity(
                user.id(), user.email(), user.passwordHash(), user.fullName(), user.role(), user.status()
        )));
    }

    @Override
    public Optional<User> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public void updatePassword(Long id, String passwordHash) {
        repository.updatePassword(id, passwordHash);
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getFullName(),
                entity.getRole(), entity.getStatus(), entity.getCreatedAt());
    }
}
