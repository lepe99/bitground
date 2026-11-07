package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.NotificationGroup;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.service.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;


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
    
    // 내 알림 목록 (group: ALL·TRADE·COMMUNITY·SYSTEM 탭, 잘못된 값이면 400)
    @GetMapping
    public PageResponse<NotificationProjection> getNotifications(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "ALL") NotificationGroup group, Pageable pageable) {
        return PageResponse.of(notificationService.findNotifications(authUser.id(), group, pageable));
    }

    // 알림 하나 읽음 (누른 알림만)
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markOneAsRead(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        notificationService.markAsRead(authUser.id(), List.of(id));
        return ResponseEntity.noContent().build();
    }

    // 고른 알림들 읽음 (목록에서 묶여 보이는 알림을 누르면 함께)
    @PatchMapping("/read")
    public ResponseEntity<Void> markSomeAsRead(@AuthenticationPrincipal AuthUser authUser, @RequestBody ReadRequest request) {
        notificationService.markAsRead(authUser.id(), request.ids());
        return ResponseEntity.noContent().build();
    }

    public record ReadRequest(List<Integer> ids) {
    }

    // 모두 읽음
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
