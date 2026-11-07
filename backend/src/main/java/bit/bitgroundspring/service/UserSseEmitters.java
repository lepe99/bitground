package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.service.NotificationService;
import bit.bitgroundspring.event.RealtimeBus;
import bit.bitgroundspring.event.RealtimeListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class UserSseEmitters {
    private final NotificationService notificationService;
    // 실시간 전송은 모든 서버를 거쳐 사용자가 붙어 있는 서버가 보낸다 (서버가 여러 대일 때)
    private final RealtimeBus realtimeBus;
    
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
    
    /** 저장해 둔 알림을 접속 중이면 실시간으로 보낸다 (댓글 알림: 문구는 저장한 것을 그대로 쓴다). 어느 서버에 접속했든 전달된다 */
    public void sendSaved(Notification notification) {
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
    
    /** 모든 사용자 알림 (공지·시즌): 저장한 뒤 접속 중인 사용자에게 보낸다 (모든 서버) */
    public void sendToAll(NotificationResponse request) {
        // 시스템 알림 저장 (접속 여부와 무관). 받은 화면이 곧바로 읽지 않은 개수를 다시 읽으므로 보내기 전에 저장한다
        if (request.getMessage() == Message.SEASON_UPDATE || request.getMessage() == Message.NOTICE) {
            saveSystemNotification(request);
        }
        realtimeBus.toAll(request);
    }

    // 시스템 알림 저장
    private void saveSystemNotification(NotificationResponse request) {
        try {
            Map<String, Object> dataMap = request.getData();
            String message = "";
            
            if (request.getMessage() == Message.SEASON_UPDATE) {
                String seasonName = (String) dataMap.get("seasonName");
                String seasonFlag = (String) dataMap.get("seasonFlag");
                message = getMessage(seasonFlag, seasonName);
            } else if (request.getMessage() == Message.NOTICE) {
                String title = (String) dataMap.get("title");
                message = String.format("🔔 새로운 공지사항이 등록되었습니다.\n공지사항 탭에서 확인해보세요.\n[%s]", title);
            }
            
            // 예전에는 1번 사용자 행 하나에 저장해 모두가 같이 읽었는데, 1번은 실제 사용자라
            // 그 사람의 주문 알림까지 모두에게 보이고 읽지 않은 개수도 셀 수 없었다
            int saved = notificationService.saveForAllUsers(request.getMessage(), message);
            log.info("시스템 알림 {}건 저장 ({})", saved, request.getMessage());
            
        } catch (Exception e) {
            log.error("시스템 알림 저장 실패: {}", e.getMessage());
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
    
    
    private String getMessage(String seasonFlag, String seasonName) {
        if (seasonFlag.equals("season")) {
            return String.format("🎉 %s 스플릿 1 🎉\n새로운 시즌이 시작되었습니다!\n이전 랭킹과 수익률을 확인해보세요.", seasonName);
        } else if (seasonFlag.equals("split")) {
            return String.format("🚀 %s 스플릿 2 🚀\n새로운 스플릿이 시작되었습니다!\n10,000,000원의 추가 자금이 지급됩니다.", seasonName);
        }
        return "";
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