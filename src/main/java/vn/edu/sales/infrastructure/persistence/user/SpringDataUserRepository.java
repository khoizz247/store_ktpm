package vn.edu.sales.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity,Long> {
    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("update UserJpaEntity u set u.passwordHash = :hash where u.id = :id")
    int updatePassword(@Param("id") Long id, @Param("hash") String hash);
}
