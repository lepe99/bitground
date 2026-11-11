package bit.bitgroundspring.user;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    /** 관리자 (문의 알림 대상) */
    @Query("SELECT u.id FROM User u WHERE u.role = bit.bitgroundspring.user.Role.ROLE_ADMIN AND u.isDeleted = false")
    java.util.List<Integer> findAdminIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithPessimisticLock(@Param("id") Integer id);
}
