package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;


public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    
    // 내 알림 (공지·시즌 알림도 사용자마다 한 행씩 저장되므로 내 것만 보면 된다). types: 목록 탭의 알림 종류
    @Query("SELECT n " +
            "FROM Notification n " +
            "WHERE n.user.id = :userId AND n.messageType IN :types " +
            "ORDER BY n.createdAt DESC, n.id DESC")
    Page<NotificationProjection> findNotificationsByUserId(@Param("userId") Integer userId,
                                                           @Param("types") Collection<Message> types, Pageable pageable);

    // 고른 알림만 읽음 (내 알림만, 묶인 알림은 여러 개). 바뀐 행 수 (이미 읽었거나 남의 알림이면 세지 않음)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id IN :ids AND n.user.id = :userId AND n.isRead = false")
    int markAsRead(@Param("ids") Collection<Integer> ids, @Param("userId") Integer userId);

    // 공지·시즌 알림을 탈퇴하지 않은 모든 사용자에게 한 행씩 저장 (읽음 여부를 사용자별로 따로 갖도록)
    @Modifying
    @Query(value = "INSERT INTO notifications (user_id, message, message_type, is_read) " +
            "SELECT u.id, :message, :messageType, 0 FROM users u WHERE u.is_deleted = 0", nativeQuery = true)
    int insertForAllUsers(@Param("message") String message, @Param("messageType") String messageType);
    
    Integer countByIsReadFalseAndUser_Id(Integer userId);
    
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n " +
            "SET n.isRead = true " +
            "WHERE n.user.id = :userId " +
            "AND n.isRead = false")
    void markAllAsRead(@Param("userId") Integer userId);
}
