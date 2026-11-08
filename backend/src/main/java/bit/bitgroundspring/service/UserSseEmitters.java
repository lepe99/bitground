package bit.bitgroundspring.service;

import bit.bitgroundspring.event.RealtimeListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 이 서버에 붙은 사용자별 SSE 연결 관리 (연결·하트비트·정리·전송).
 * 알림 저장과 여러 서버로의 전달은 NotificationService·RealtimeBus가, 받은 알림을 여기로 넘기는 것은 RealtimeListener가 맡는다
 */
@Component
@Slf4j
public class UserSseEmitters {

    // 사용자별 SSE 연결 관리
    private final Map<Integer, List<EmitterWrapper>> userEmitters = new ConcurrentHashMap<>();
    
    // 📝 EmitterWrapper - 연결 시간과 상태 관리를 위한 래퍼 클래스
    @Getter
    private static class EmitterWrapper {
        // Getters
        private final SseEmitter emitter;
        private final LocalDateTime connectedAt;
        private LocalDateTime lastHeartbeatAt;
        private boolean isAlive;
        
        public EmitterWrapper(SseEmitter emitter) {
            this.emitter = emitter;
            this.connectedAt = LocalDateTime.now();
            this.lastHeartbeatAt = LocalDateTime.now();
            this.isAlive = true;
        }
        
        public void updateHeartbeat() {
            this.lastHeartbeatAt = LocalDateTime.now();
            this.isAlive = true;
        }
        
        public void markAsDead() {
            this.isAlive = false;
        }
    }
    
    public void addUser(Integer userId, SseEmitter emitter) {
        EmitterWrapper wrapper = new EmitterWrapper(emitter);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(wrapper);
        
        // 연결 관리 콜백 설정
        emitter.onTimeout(() -> removeUser(userId, emitter));
        emitter.onCompletion(() -> removeUser(userId, emitter));
        emitter.onError((ex) -> removeUser(userId, emitter));
        
        log.debug("사용자 {} 연결됨. 총 연결: {}", userId, getUserConnectionCount(userId));
    }
    

    /** 이미 JSON으로 만든 알림을 이 서버에 연결된 사용자에게 보낸다 ({@link RealtimeListener}) */
    public boolean sendJsonToUser(Integer userId, String json) {
        return send(userId, json, MediaType.APPLICATION_JSON);
    }

    /** 이 서버에 연결된 모든 사용자에게 */
    public void sendJsonToAllLocal(String json) {
        getOnlineUsers().forEach(userId -> sendJsonToUser(userId, json));
    }

    private boolean send(Integer userId, Object data, MediaType mediaType) {
        List<EmitterWrapper> wrappers = userEmitters.get(userId);
        if (wrappers == null || wrappers.isEmpty()) {
            return false;
        }
        
        List<EmitterWrapper> wrappersCopy = new ArrayList<>(wrappers);
        List<EmitterWrapper> deadWrappers = new ArrayList<>();
        boolean hasSuccessfulSend = false;
        
        for (EmitterWrapper wrapper : wrappersCopy) {
            try {
                wrapper.getEmitter().send(mediaType == null ? SseEmitter.event().data(data) : SseEmitter.event().data(data, mediaType));
                wrapper.updateHeartbeat();
                hasSuccessfulSend = true;
            } catch (Exception e) {
                // 탭을 닫거나 새로고침해 끊긴 연결은 서버가 보낼 때야 알게 된다 (Broken pipe 등): 정상이라 DEBUG
                if (isClientGone(e)) {
                    log.debug("사용자 {}의 끊긴 SSE 연결 정리: {}", userId, e.getMessage());
                } else {
                    log.warn("사용자 {}에게 SSE 전송 실패: {}", userId, e.toString());
                }
                wrapper.markAsDead();
                deadWrappers.add(wrapper);
            }
        }
        
        // 죽은 연결 정리
        cleanupDeadWrappers(userId, deadWrappers);
        
        return hasSuccessfulSend;
    }
    
    /** 클라이언트가 이미 연결을 끊어 생긴 오류인지 (서버 문제가 아님) */
    static boolean isClientGone(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AsyncRequestNotUsableException || t instanceof java.io.IOException
                    || t instanceof IllegalStateException) { // 이미 완료된 emitter
                return true;
            }
        }
        return false;
    }

    // 하트비트 전송 - 30초마다 실행
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (userEmitters.isEmpty()) {
            return;
        }
        
        Set<Integer> userIds = new HashSet<>(userEmitters.keySet());
        int totalFailed = 0;
        
        for (Integer userId : userIds) {
            List<EmitterWrapper> wrappers = userEmitters.get(userId);
            if (wrappers == null) continue;
            
            List<EmitterWrapper> wrappersCopy = new ArrayList<>(wrappers);
            List<EmitterWrapper> deadWrappers = new ArrayList<>();
            
            for (EmitterWrapper wrapper : wrappersCopy) {
                try {
                    wrapper.getEmitter().send(SseEmitter.event()
                            .name("heartbeat")
                            .data("ping"));
                    wrapper.updateHeartbeat();
                } catch (Exception e) {
                    wrapper.markAsDead();
                    deadWrappers.add(wrapper);
                    totalFailed++;
                }
            }
            
            cleanupDeadWrappers(userId, deadWrappers);
        }
        
        if (totalFailed > 0) {
            log.debug("하트비트 정리 완료. 정리된 연결: {}", totalFailed);
        }
    }
    
    // 죽은 연결 정리 - 2분마다 실행
    @Scheduled(fixedRate = 120000)
    public void cleanupStaleConnections() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2); // 2분 이상 응답 없음
        
        Set<Integer> userIds = new HashSet<>(userEmitters.keySet());
        int cleanedUp = 0;
        
        for (Integer userId : userIds) {
            List<EmitterWrapper> wrappers = userEmitters.get(userId);
            if (wrappers == null) continue;
            
            List<EmitterWrapper> staleWrappers = wrappers.stream()
                    .filter(wrapper -> !wrapper.isAlive() ||
                            wrapper.getLastHeartbeatAt().isBefore(threshold))
                    .collect(Collectors.toList());
            
            if (!staleWrappers.isEmpty()) {
                cleanupDeadWrappers(userId, staleWrappers);
                cleanedUp += staleWrappers.size();
            }
        }
        
        if (cleanedUp > 0) {
            log.debug("오래된 연결 정리됨: {}", cleanedUp);
        }
    }
    
    // 죽은 연결 정리 헬퍼 메서드
    private void cleanupDeadWrappers(Integer userId, List<EmitterWrapper> deadWrappers) {
        if (deadWrappers.isEmpty()) return;
        
        List<EmitterWrapper> userWrappers = userEmitters.get(userId);
        if (userWrappers != null) {
            userWrappers.removeAll(deadWrappers);
            if (userWrappers.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
        
        // SseEmitter 명시적 완료 처리
        for (EmitterWrapper wrapper : deadWrappers) {
            try {
                wrapper.getEmitter().complete();
            } catch (Exception e) {
                // 이미 닫힌 연결일 수 있으므로 무시
            }
        }
    }
    
    public void removeUser(Integer userId, SseEmitter emitter) {
        List<EmitterWrapper> wrappers = userEmitters.get(userId);
        if (wrappers != null) {
            wrappers.removeIf(wrapper -> wrapper.getEmitter().equals(emitter));
            if (wrappers.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
        log.debug("사용자 {} 연결 해제됨", userId);
    }
    
    public Set<Integer> getOnlineUsers() {
        return userEmitters.keySet().stream()
                .filter(userId -> {
                    List<EmitterWrapper> wrappers = userEmitters.get(userId);
                    return wrappers != null && !wrappers.isEmpty();
                })
                .collect(Collectors.toSet());
    }
    
    public int getUserConnectionCount(Integer userId) {
        List<EmitterWrapper> wrappers = userEmitters.get(userId);
        return wrappers != null ? wrappers.size() : 0;
    }
    
    
    // 🔄 애플리케이션 종료 시 모든 연결 정리
    @PreDestroy
    public void cleanup() {
        if (!userEmitters.isEmpty()) {
            log.info("SSE 연결 정리 중...");
            
            userEmitters.values().forEach(wrappers ->
                    wrappers.forEach(wrapper -> {
                        try {
                            wrapper.getEmitter().complete();
                        } catch (Exception e) {
                            // 무시
                        }
                    })
            );
            
            userEmitters.clear();
            log.info("SSE 연결 정리 완료");
        }
    }
}