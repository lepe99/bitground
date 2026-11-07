package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.entity.NotificationGroup;
import bit.bitgroundspring.dto.NotificationSettingDto;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.service.NotificationSettingService;
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
    private final NotificationSettingService notificationSettingService;
    
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

    // 고른 알림 읽음 (누른 알림, 묶여 보이는 알림이면 함께)
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
    
    // 고른 알림 지우기 (?ids=1,2,3, 내 알림만)
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser authUser, @RequestParam List<Integer> ids) {
        notificationService.delete(authUser.id(), ids);
        return ResponseEntity.noContent().build();
    }

    // 읽은 알림 모두 지우기
    @DeleteMapping("/read")
    public ResponseEntity<Void> deleteRead(@AuthenticationPrincipal AuthUser authUser) {
        notificationService.deleteRead(authUser.id());
        return ResponseEntity.noContent().build();
    }

    // 알림 설정 (종류별 받기, 실시간 토스트)
    @GetMapping("/settings")
    public NotificationSettingDto getSettings(@AuthenticationPrincipal AuthUser authUser) {
        return notificationSettingService.get(authUser.id());
    }

    // 알림 설정 바꾸기 (보낸 항목만 바뀜)
    @PutMapping("/settings")
    public NotificationSettingDto updateSettings(@AuthenticationPrincipal AuthUser authUser,
                                                 @RequestBody NotificationSettingDto request) {
        return notificationSettingService.update(authUser.id(), request);
    }

    @GetMapping("/count")
    public ResponseEntity<Integer> countUnreadNotifications(
            @AuthenticationPrincipal AuthUser authUser) {
        Integer userId = authUser.id();
        return ResponseEntity.ok(notificationService.countUnreadNotifications(userId));
    }
}
