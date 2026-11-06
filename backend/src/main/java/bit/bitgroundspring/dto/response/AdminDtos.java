package bit.bitgroundspring.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** 관리자 페이지 응답 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** 운영 현황 */
    public record Overview(Users users, Trades trades, CurrentSeasonDto season, long participants,
                           List<SchedulerRun> schedulerRuns, Ai ai) {
    }

    public record Users(long total, long joinedToday, long activeToday, long suspended, long withdrawn) {
    }

    /** 오늘 체결(시장가·예약)과 대기 중인 예약 주문 */
    public record Trades(long marketToday, long reservedToday, long pendingReserved) {
    }

    /** 시즌 정산·스플릿 실행 기록 (scheduler_runs) */
    public record SchedulerRun(String kind, String period, LocalDateTime ranAt, LocalDateTime notifiedAt) {
    }

    /** 오늘 AI 인사이트와 시즌 리뷰 생성 현황 */
    public record Ai(long insightsToday, List<String> insightSymbols, String insightModel, String insightPromptVersion,
                     long reviewsTotal, long reviewsToday, String reviewModel, String reviewPromptVersion) {
    }

    /** 사용자 목록 한 줄 (이메일은 일부 가림). 닉네임은 겹칠 수 있어 화면은 id로 구분한다 */
    public record UserRow(int id, String name, String profileImage, String provider, String email, boolean admin, int tier,
                          boolean withdrawn, LocalDateTime suspendedAt, LocalDateTime createdAt) {
    }

    public record UserDetail(UserRow user, String suspendedReason, long cash, Integer rank, Long totalValue, Integer currentTier,
                             long posts, long comments, long trades, List<RecentOrder> recentOrders) {
    }

    public record RecentOrder(int id, String coin, String orderType, String status, double amount, Double price,
                              boolean reserved, LocalDateTime createdAt) {
    }

    public record PostRow(int id, String title, String category, boolean deleted, long comments,
                          int authorId, String authorName, LocalDateTime createdAt) {
    }

    public record CommentRow(int id, String content, int postId, String postTitle,
                             int authorId, String authorName, LocalDateTime createdAt) {
    }

    public record AuditRow(int id, int adminId, String adminName, String action, String targetType, Integer targetId,
                           String detail, LocalDateTime createdAt) {
    }
}
