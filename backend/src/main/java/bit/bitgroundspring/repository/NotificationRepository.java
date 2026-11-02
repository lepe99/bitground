package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.Notification;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;


public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    
    @Query("SELECT n " +
            "FROM Notification n " +
            "WHERE n.user.id = :userId OR n.user.id = 1 " +
            "ORDER BY n.createdAt DESC")
    Page<NotificationProjection> findNotificationsByUserIdOrAll(@Param("userId") Integer userId, Pageable pageable);
    
    Integer countByIsReadFalseAndUser_Id(Integer userId);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n " +
            "SET n.isRead = true " +
            "WHERE n.user.id = :userId " +
            "AND n.isRead = false")
    void markAllAsRead(@Param("userId") Integer userId);
}
