package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.projection.NotificationProjection;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.security.oauth2.AuthService;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.util.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {
    
    private final UserSseEmitters userSseEmitters;
    private final AuthService authService;
    private final NotificationService notificationService;
    
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30분
    
    // 사용자별 SSE 구독 엔드포인트
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CookieValue(name = "jwt_token", required = false) String jwtToken) {
        // JWT 토큰에서 사용자 ID 추출
        Integer userId = authService.getUserIdFromToken(jwtToken);
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        userSseEmitters.addUser(userId, emitter);
        
        return emitter;
    }
    
    // 여러 유저에게 알림 전송
    @PostMapping("/send")
    public ResponseEntity<Map<String, Integer>> sendToMultipleUsers(
            @RequestBody NotificationResponse request) {
        Map<String, Integer> results = userSseEmitters.sendToAll(request); // 모든 사용자에게 알림 전송
        return ResponseEntity.ok(results);
    }
    
    // 나한테 온 알림, 전체 알림, 안읽은 알림 개수 조회
    @GetMapping
    public ResponseEntity<Page<NotificationProjection>> getNotifications(
            @CookieValue(name = "jwt_token", required = false) String jwtToken, Pageable pageable) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        return ResponseEntity.ok(notificationService.findNotifications(userId, pageable));
    }
    
    @PatchMapping
    public ResponseEntity<Void> markAsRead(
            @CookieValue(name = "jwt_token", required = false) String jwtToken) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/count")
    public ResponseEntity<Integer> countUnreadNotifications(
            @CookieValue(name = "jwt_token", required = false) String jwtToken) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        System.out.println("asdf" + notificationService.countUnreadNotifications(userId));
        return ResponseEntity.ok(notificationService.countUnreadNotifications(userId));
    }
}
