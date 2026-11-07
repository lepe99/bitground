package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.NotificationGroup;
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
    
    // 모든 사용자에게 같은 알림 저장 (공지·시즌 알림). 저장한 행 수 반환
    @Transactional
    public int saveForAllUsers(Message messageType, String message) {
        return notificationRepository.insertForAllUsers(message, messageType.name());
    }

    // 사용자별 알림 조회 (탭: 전체·거래·커뮤니티·공지·시즌)
    public Page<NotificationProjection> findNotifications(Integer userId, NotificationGroup group, Pageable pageable) {
        return notificationRepository.findNotificationsByUserId(userId, group.types(), pageable);
    }

    // 알림 하나 읽음 (내 알림만, 이미 읽었으면 그대로)
    @Transactional
    public void markAsRead(Integer userId, Integer notificationId) {
        notificationRepository.markAsRead(notificationId, userId);
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
