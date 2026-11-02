package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    
    // 알림 저장
    public void saveNotification(Notification notification) {
        notificationRepository.save(notification);
    }
    
    // 사용자별 알림 조회
    public Page<NotificationProjection> findNotifications(Integer userId, Pageable pageable) {
        return notificationRepository.findNotificationsByUserIdOrAll(userId, pageable);
    }
    
    // userId가 일치하는 isRead 상태 업데이트
    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsRead(userId);
    }
    
    // 읽지 않은 알림 개수 조회
    public Integer countUnreadNotifications(Integer userId) {
        return notificationRepository.countByIsReadFalseAndUser_Id(userId);
    }
    
}
