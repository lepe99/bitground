package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.service.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {
    
    private final UserSseEmitters userSseEmitters;
    private final NotificationService notificationService;
    
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분
    
    // 사용자별 SSE 구독 엔드포인트
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal AuthUser authUser) {
        // JWT 토큰에서 사용자 ID 추출
        Integer userId = authUser.id();
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        userSseEmitters.addUser(userId, emitter);
        
        return emitter;
    }
    
    // 나한테 온 알림, 전체 알림, 안읽은 알림 개수 조회
    @GetMapping
    public PageResponse<NotificationProjection> getNotifications(
            @AuthenticationPrincipal AuthUser authUser, Pageable pageable) {
        Integer userId = authUser.id();
        return PageResponse.of(notificationService.findNotifications(userId, pageable));
    }
    
    @PatchMapping
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal AuthUser authUser) {
        Integer userId = authUser.id();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/count")
    public ResponseEntity<Integer> countUnreadNotifications(
            @AuthenticationPrincipal AuthUser authUser) {
        Integer userId = authUser.id();
        return ResponseEntity.ok(notificationService.countUnreadNotifications(userId));
    }
}
