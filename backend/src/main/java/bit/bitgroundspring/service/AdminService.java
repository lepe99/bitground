package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.dto.response.AdminDtos.*;
import bit.bitgroundspring.dto.response.CurrentSeasonDto;
import bit.bitgroundspring.entity.Comment;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.PostRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthTokenService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 관리자 페이지 (마이페이지의 관리자 탭). 조회는 집계·목록이라 SQL로 바로 읽고,
 * 정지·삭제처럼 바꾸는 작업은 모두 작업 기록(admin_audit_logs)을 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int RECENT_ORDERS = 10;
    private static final int SCHEDULER_RUNS = 8;

    private final NamedParameterJdbcTemplate jdbc;
    private final SeasonService seasonService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final AuthTokenService authTokenService;
    private final AdminAuditService audit;

    // ── 운영 현황 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Overview overview() {
        var today = new MapSqlParameterSource("today", LocalDate.now().atStartOfDay());

        Users users = jdbc.queryForObject("""
                SELECT SUM(is_deleted = 0) total,
                       SUM(is_deleted = 0 AND created_at >= :today) joined_today,
                       SUM(is_deleted = 0 AND suspended_at IS NOT NULL) suspended,
                       SUM(is_deleted = 1) withdrawn,
                       (SELECT COUNT(DISTINCT user_id) FROM orders WHERE status = 'COMPLETED' AND updated_at >= :today) active_today
                FROM users""", today, (rs, i) -> new Users(
                rs.getLong("total"), rs.getLong("joined_today"), rs.getLong("active_today"),
                rs.getLong("suspended"), rs.getLong("withdrawn")));

        // 예약 주문은 reserve_price가 있다 (시장가 주문은 바로 체결되어 없음)
        Trades trades = jdbc.queryForObject("""
                SELECT SUM(status = 'COMPLETED' AND updated_at >= :today AND reserve_price IS NULL) market_today,
                       SUM(status = 'COMPLETED' AND updated_at >= :today AND reserve_price IS NOT NULL) reserved_today,
                       SUM(status = 'PENDING') pending
                FROM orders""", today, (rs, i) -> new Trades(
                rs.getLong("market_today"), rs.getLong("reserved_today"), rs.getLong("pending")));

        CurrentSeasonDto season = null;
        long participants = 0;
        try {
            season = seasonService.getCurrentSeason();
            participants = count("SELECT COUNT(*) FROM user_rankings WHERE season_id = :id",
                    new MapSqlParameterSource("id", season.id()));
        } catch (BusinessException e) {
            // 진행 중인 시즌이 없으면 비워 둔다
        }

        List<SchedulerRun> runs = jdbc.query("""
                SELECT kind, period, created_at, notified_at FROM scheduler_runs
                ORDER BY created_at DESC LIMIT :limit""", Map.of("limit", SCHEDULER_RUNS), (rs, i) -> new SchedulerRun(
                rs.getString("kind"), rs.getString("period"), time(rs, "created_at"), time(rs, "notified_at")));

        return new Overview(users, trades, season, participants, runs, ai());
    }

    private Ai ai() {
        var params = new MapSqlParameterSource()
                .addValue("date", LocalDate.now())
                .addValue("today", LocalDate.now().atStartOfDay());
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM ai_insights WHERE date = :date) insights,
                       (SELECT GROUP_CONCAT(symbol ORDER BY id SEPARATOR ',') FROM ai_insights WHERE date = :date) symbols,
                       (SELECT model FROM ai_insights WHERE date = :date ORDER BY id DESC LIMIT 1) insight_model,
                       (SELECT prompt_version FROM ai_insights WHERE date = :date ORDER BY id DESC LIMIT 1) insight_prompt,
                       (SELECT COUNT(*) FROM ai_advices) reviews,
                       (SELECT COUNT(*) FROM ai_advices WHERE created_at >= :today) reviews_today,
                       (SELECT model FROM ai_advices ORDER BY id DESC LIMIT 1) review_model,
                       (SELECT prompt_version FROM ai_advices ORDER BY id DESC LIMIT 1) review_prompt""",
                params, (rs, i) -> {
                    String symbols = rs.getString("symbols");
                    return new Ai(rs.getLong("insights"), symbols == null ? List.of() : Arrays.asList(symbols.split(",")),
                            rs.getString("insight_model"), rs.getString("insight_prompt"),
                            rs.getLong("reviews"), rs.getLong("reviews_today"),
                            rs.getString("review_model"), rs.getString("review_prompt"));
                });
    }

    // ── 사용자 ────────────────────────────────────────────────

    /**
     * 사용자 검색. 닉네임은 겹칠 수 있으므로 번호·이메일로도 정확히 찾는다:
     * "#33"이나 "33" → 번호, "@"가 있으면 → 이메일 전체 일치 (화면에는 가린 채로), 그 밖에는 닉네임 일부
     */
    @Transactional(readOnly = true)
    public PageResponse<UserRow> users(String query, int page, int size) {
        String q = query == null ? "" : query.trim();
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size);
        String where;
        if (q.isEmpty()) {
            where = "";
        } else if (q.matches("#?\\d{1,9}")) {
            where = " WHERE id = :id";
            params.addValue("id", Long.parseLong(q.replace("#", "")));
        } else if (q.contains("@")) {
            where = " WHERE LOWER(email) = LOWER(:email)";
            params.addValue("email", q);
        } else {
            where = " WHERE name LIKE :name";
            params.addValue("name", "%" + q + "%");
        }
        List<UserRow> rows = jdbc.query("SELECT * FROM users" + where + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                params, (rs, i) -> userRow(rs));
        long total = count("SELECT COUNT(*) FROM users" + where, params);
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(page, size), total));
    }

    @Transactional(readOnly = true)
    public UserDetail user(int userId) {
        var id = new MapSqlParameterSource("id", userId);
        List<UserDetail> found = jdbc.query("""
                SELECT u.*,
                       (SELECT ur.ranks FROM user_rankings ur JOIN seasons s ON s.id = ur.season_id
                        WHERE ur.user_id = u.id AND s.status = 'PENDING' LIMIT 1) ranks,
                       (SELECT ur.total_value FROM user_rankings ur JOIN seasons s ON s.id = ur.season_id
                        WHERE ur.user_id = u.id AND s.status = 'PENDING' LIMIT 1) total_value,
                       (SELECT ur.tier FROM user_rankings ur JOIN seasons s ON s.id = ur.season_id
                        WHERE ur.user_id = u.id AND s.status = 'PENDING' LIMIT 1) current_tier,
                       (SELECT COUNT(*) FROM posts WHERE user_id = u.id AND is_deleted = 0) post_count,
                       (SELECT COUNT(*) FROM comments WHERE user_id = u.id) comment_count,
                       (SELECT COUNT(*) FROM orders WHERE user_id = u.id AND status = 'COMPLETED') trade_count
                FROM users u WHERE u.id = :id""", id, (rs, i) -> new UserDetail(
                userRow(rs), rs.getString("suspended_reason"), rs.getLong("cash"),
                (Integer) rs.getObject("ranks", Integer.class), (Long) rs.getObject("total_value", Long.class),
                (Integer) rs.getObject("current_tier", Integer.class),
                rs.getLong("post_count"), rs.getLong("comment_count"), rs.getLong("trade_count"), List.of()));
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        List<RecentOrder> orders = jdbc.query("""
                SELECT o.id, c.korean_name, o.order_type, o.status, o.amount, o.trade_price, o.reserve_price, o.created_at
                FROM orders o JOIN coins c ON c.id = o.symbol_id
                WHERE o.user_id = :id ORDER BY o.id DESC LIMIT :limit""",
                id.addValue("limit", RECENT_ORDERS), (rs, i) -> {
                    Double reserve = (Double) rs.getObject("reserve_price", Double.class);
                    Double trade = (Double) rs.getObject("trade_price", Double.class);
                    return new RecentOrder(rs.getInt("id"), rs.getString("korean_name"), rs.getString("order_type"),
                            rs.getString("status"), rs.getDouble("amount"), trade != null ? trade : reserve,
                            reserve != null, time(rs, "created_at"));
                });
        UserDetail d = found.getFirst();
        return new UserDetail(d.user(), d.suspendedReason(), d.cash(), d.rank(), d.totalValue(), d.currentTier(),
                d.posts(), d.comments(), d.trades(), orders);
    }

    /** 이용 정지: 모든 로그인 세션을 끊고, 이후 로그인과 API 이용을 막는다 */
    @Transactional
    public void suspend(AuthUser admin, int userId, String reason) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole().isAdmin()) {
            throw new BusinessException(ErrorCode.CANNOT_SUSPEND_ADMIN);
        }
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정지 사유를 1~200자로 입력해주세요.");
        }
        user.setSuspendedAt(LocalDateTime.now());
        user.setSuspendedReason(trimmed);
        authTokenService.revokeAll(userId);
        audit.record(admin.id(), "USER_SUSPEND", "USER", userId, user.getName() + " · " + trimmed);
    }

    @Transactional
    public void unsuspend(AuthUser admin, int userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setSuspendedAt(null);
        user.setSuspendedReason(null);
        audit.record(admin.id(), "USER_UNSUSPEND", "USER", userId, user.getName());
    }

    // ── 커뮤니티 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<PostRow> posts(int page, int size) {
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size);
        List<PostRow> rows = jdbc.query("""
                SELECT p.id, p.title, p.category, p.is_deleted, p.created_at, u.id author_id, u.name author_name,
                       (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) comment_count
                FROM posts p JOIN users u ON u.id = p.user_id
                ORDER BY p.id DESC LIMIT :limit OFFSET :offset""", params, (rs, i) -> new PostRow(
                rs.getInt("id"), rs.getString("title"), rs.getString("category"), rs.getBoolean("is_deleted"),
                rs.getLong("comment_count"), rs.getInt("author_id"), rs.getString("author_name"), time(rs, "created_at")));
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(page, size), count("SELECT COUNT(*) FROM posts", params)));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentRow> comments(int page, int size) {
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size);
        List<CommentRow> rows = jdbc.query("""
                SELECT c.id, c.content, c.post_id, p.title post_title, c.created_at, u.id author_id, u.name author_name
                FROM comments c JOIN users u ON u.id = c.user_id JOIN posts p ON p.id = c.post_id
                ORDER BY c.id DESC LIMIT :limit OFFSET :offset""", params, (rs, i) -> new CommentRow(
                rs.getInt("id"), rs.getString("content"), rs.getInt("post_id"), rs.getString("post_title"),
                rs.getInt("author_id"), rs.getString("author_name"), time(rs, "created_at")));
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(page, size), count("SELECT COUNT(*) FROM comments", params)));
    }

    @Transactional
    public void deletePost(AuthUser admin, int postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postService.deletePost(postId, admin);
        audit.record(admin.id(), "POST_DELETE", "POST", postId, post.getTitle() + " (작성자 " + post.getUser().getName() + ")");
    }

    @Transactional
    public void deleteComment(AuthUser admin, int commentId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        String detail = comment.getContent() + " (작성자 " + comment.getUser().getName() + ", 게시글 " + comment.getPost().getId() + ")";
        commentService.deleteComment(commentId, admin);
        audit.record(admin.id(), "COMMENT_DELETE", "COMMENT", commentId, detail);
    }

    // ── 작업 기록 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AuditRow> auditLogs(int page, int size) {
        var params = new MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size);
        List<AuditRow> rows = jdbc.query("""
                SELECT a.*, u.name admin_name FROM admin_audit_logs a JOIN users u ON u.id = a.admin_id
                ORDER BY a.id DESC LIMIT :limit OFFSET :offset""", params, (rs, i) -> new AuditRow(
                rs.getInt("id"), rs.getInt("admin_id"), rs.getString("admin_name"), rs.getString("action"),
                rs.getString("target_type"), (Integer) rs.getObject("target_id", Integer.class), rs.getString("detail"),
                time(rs, "created_at")));
        return PageResponse.of(new PageImpl<>(rows, PageRequest.of(page, size), count("SELECT COUNT(*) FROM admin_audit_logs", params)));
    }

    // ── 공통 ──────────────────────────────────────────────────

    private UserRow userRow(ResultSet rs) throws SQLException {
        return new UserRow(rs.getInt("id"), rs.getString("name"), rs.getString("profile_image"), rs.getString("provider"),
                maskEmail(rs.getString("email")),
                "ROLE_ADMIN".equals(rs.getString("role")), rs.getInt("tier"), rs.getBoolean("is_deleted"),
                time(rs, "suspended_at"), time(rs, "created_at"));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    /** 관리자에게도 개인정보는 필요한 만큼만: ab***@kakao.com */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
