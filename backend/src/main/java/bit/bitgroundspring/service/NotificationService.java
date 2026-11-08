package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.event.RealtimeBus;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.NotificationGroup;
import bit.bitgroundspring.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 알림 저장·조회와 실시간 전송. 문구는 {@link NotificationMessages}, 접속 관리는 {@link UserSseEmitters}가 맡는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    // 실시간 전송은 모든 서버를 거쳐 사용자가 붙어 있는 서버가 보낸다 (서버가 여러 대일 때)
    private final RealtimeBus realtimeBus;

    // 모든 사용자에게 같은 알림 저장 (공지·시즌 알림). 저장한 행 수 반환
    @Transactional
    public int saveForAllUsers(Message messageType, String message) {
        return notificationRepository.insertForAllUsers(message, messageType.name());
    }

    /**
     * 모든 사용자 알림 (공지·시즌): 저장한 뒤 접속 중인 사용자에게 같은 문구를 보낸다.
     * 받은 화면이 곧바로 읽지 않은 개수를 다시 읽으므로 보내기 전에 저장한다. 저장이 실패해도 실시간 알림은 보낸다
     */
    public void broadcast(Message messageType, String text) {
        try {
            int saved = saveForAllUsers(messageType, text);
            log.info("시스템 알림 {}건 저장 ({})", saved, messageType);
        } catch (RuntimeException e) {
            log.error("시스템 알림 저장 실패 ({})", messageType, e);
        }
        realtimeBus.toAll(NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(messageType)
                .data(Map.of("text", text))
                .build());
    }

    /** 저장해 둔 알림을 접속 중이면 실시간으로 보낸다 (문구는 저장한 것 그대로). 어느 서버에 접속했든 전달된다 */
    public void push(Notification notification) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", notification.getMessage());
        data.put("postId", notification.getPostId());
        data.put("id", notification.getId()); // 토스트를 누르면 이 알림을 읽음으로
        realtimeBus.toUser(notification.getUser().getId(), NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(notification.getMessageType())
                .data(data)
                .build());
    }

    // 사용자별 알림 조회 (탭: 전체·거래·커뮤니티·공지·시즌)
    public Page<NotificationProjection> findNotifications(Integer userId, NotificationGroup group, Pageable pageable) {
        return notificationRepository.findNotificationsByUserId(userId, group.types(), pageable);
    }

    // 고른 알림 읽음 (내 알림만, 이미 읽었으면 그대로). 한 번에 최대 100개
    @Transactional
    public void markAsRead(Integer userId, Collection<Integer> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        if (notificationIds.size() > MAX_READ_IDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 번에 읽음 처리할 수 있는 알림은 " + MAX_READ_IDS + "개입니다.");
        }
        notificationRepository.markAsRead(notificationIds, userId);
    }

    private static final int MAX_READ_IDS = 100;

    // 고른 알림 지우기 (내 알림만, 묶여 보이는 알림이면 함께). 한 번에 최대 100개
    @Transactional
    public void delete(Integer userId, Collection<Integer> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        if (notificationIds.size() > MAX_READ_IDS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 번에 지울 수 있는 알림은 " + MAX_READ_IDS + "개입니다.");
        }
        notificationRepository.deleteByIds(notificationIds, userId);
    }

    // 읽은 알림 모두 지우기. 지운 개수
    @Transactional
    public int deleteRead(Integer userId) {
        return notificationRepository.deleteRead(userId);
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
