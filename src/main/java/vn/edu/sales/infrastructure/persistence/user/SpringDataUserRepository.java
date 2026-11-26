package vn.edu.sales.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity,Long> {
    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    // Tăng token_version để mọi JWT cấp trước lần đổi mật khẩu này hết hiệu lực.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserJpaEntity u set u.passwordHash = :hash, u.tokenVersion = u.tokenVersion + 1, "
            + "u.updatedAt = CURRENT_TIMESTAMP where u.id = :id")
    int updatePassword(@Param("id") Long id, @Param("hash") String hash);
}
