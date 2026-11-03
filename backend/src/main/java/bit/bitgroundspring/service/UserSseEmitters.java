package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.service.NotificationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
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
    
    public boolean sendToUser(Integer userId, NotificationResponse data) {
        List<EmitterWrapper> wrappers = userEmitters.get(userId);
        if (wrappers == null || wrappers.isEmpty()) {
            return false;
        }
        
        List<EmitterWrapper> wrappersCopy = new ArrayList<>(wrappers);
        List<EmitterWrapper> deadWrappers = new ArrayList<>();
        boolean hasSuccessfulSend = false;
        
        for (EmitterWrapper wrapper : wrappersCopy) {
            try {
                wrapper.getEmitter().send(SseEmitter.event().data(data));
                wrapper.updateHeartbeat();
                hasSuccessfulSend = true;
            } catch (Exception e) {
                log.error("사용자 {}에게 데이터 전송 중 오류 발생: {}", userId, e.getMessage());
                wrapper.markAsDead();
                deadWrappers.add(wrapper);
            }
        }
        
        // 죽은 연결 정리
        cleanupDeadWrappers(userId, deadWrappers);
        
        return hasSuccessfulSend;
    }
    
    /**
     * 예약 주문 체결 알림: 알림 목록에 먼저 저장하고, 접속 중이면 실시간으로도 보낸다.
     * (기존: SSE 전송에 성공했을 때만 저장해서, 접속하지 않은 사이 체결된 주문은 알림이 남지 않음)
     */
    public void notifyOrderExecution(Integer userId, NotificationResponse data) {
        saveNotification(userId, data, Message.ORDER_EXECUTION);
        sendToUser(userId, data);
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
    
    // 알림 저장 메서드
    private void saveNotification(Integer userId, NotificationResponse data, Message messageType) {
        try {
            Map<String, Object> dataMap = data.getData();
            String message = "";
            
            if (messageType == Message.ORDER_EXECUTION) {
                String orderType = dataMap.get("orderType").equals("BUY") ? "매수" : "매도";
                String symbol = (String) dataMap.get("symbol");
                String cutSymbol = symbol.split("-")[1];
                BigDecimal amount = (BigDecimal) dataMap.get("amount");
                BigDecimal tradePrice = (BigDecimal) dataMap.get("tradePrice");
                String formattedPrice = formatPrice(tradePrice);
                message = String.format("예약 %s 주문이 체결되었습니다.\n수량 : %.7f %s\n체결 : 개당 %s원",
                        orderType, amount, cutSymbol, formattedPrice);
            }
            
            Notification notification = Notification.builder()
                    .user(User.builder().id(userId).build())
                    .messageType(messageType)
                    .message(message)
                    .build();
            notificationService.saveNotification(notification);
            
        } catch (Exception e) {
            log.error("알림 저장 실패 - 사용자: {}, 오류: {}", userId, e.getMessage());
        }
    }
    
    // 병렬 처리로 성능 개선
    public Map<String, Integer> sendToAll(NotificationResponse request) {
        Set<Integer> userIds = getOnlineUsers();
        
        Map<Boolean, List<Integer>> results = userIds.parallelStream()
                .collect(Collectors.partitioningBy(userId -> sendToUser(userId, request)));
        
        int successCount = results.get(true).size();
        int failureCount = results.get(false).size();
        
        Map<String, Integer> result = new HashMap<>();
        result.put("success", successCount);
        result.put("failure", failureCount);
        
        log.info("모든 사용자에게 알림 전송 완료. 성공: {}, 실패: {}", successCount, failureCount);
        
        // 시스템 알림 저장 (접속 여부와 무관)
        if (request.getMessage() == Message.SEASON_UPDATE || request.getMessage() == Message.NOTICE) {
            saveSystemNotification(request);
        }
        
        return result;
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
            
            Notification notification = Notification.builder()
                    .user(User.builder().id(1).build())
                    .messageType(request.getMessage())
                    .message(message)
                    .build();
            notificationService.saveNotification(notification);
            
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
    
    // 가격 형식 지정 메소드 - 불필요한 0 제거
    private String formatPrice(BigDecimal price) {
        if (price == null) return "0";

        // 정수인 경우 소수점 표시 안 함, 소수점이 있으면 불필요한 0 제거 (천 단위 구분)
        BigDecimal stripped = price.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return String.format("%,d", stripped.longValueExact());
        }
        return String.format("%,." + stripped.scale() + "f", stripped);
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