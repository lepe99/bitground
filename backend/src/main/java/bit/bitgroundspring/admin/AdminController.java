package bit.bitgroundspring.admin;

import bit.bitgroundspring.admin.dto.AdminDtos.*;
import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.season.dto.CurrentSeasonDto;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 관리자 페이지 API. 모두 ROLE_ADMIN만 (SecurityConfig의 /admin/**).
 * 화면에서 탭을 숨기는 것은 편의일 뿐이고 권한은 여기서 막는다.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 50;

    private final AdminService adminService;
    private final AdminSeasonService adminSeasonService;

    @GetMapping("/overview")
    public Overview overview() {
        return adminService.overview();
    }

    @GetMapping("/users")
    public PageResponse<UserRow> users(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return adminService.users(q, Math.max(page, 0), clamp(size));
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable int id) {
        return adminService.user(id);
    }

    public record SuspendRequest(String reason) {
    }

    @PostMapping("/users/{id}/suspend")
    public ResponseEntity<Void> suspend(@AuthenticationPrincipal AuthUser admin, @PathVariable int id,
                                        @RequestBody SuspendRequest request) {
        adminService.suspend(admin, id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/unsuspend")
    public ResponseEntity<Void> unsuspend(@AuthenticationPrincipal AuthUser admin, @PathVariable int id) {
        adminService.unsuspend(admin, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    public PageResponse<PostRow> posts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.posts(Math.max(page, 0), clamp(size));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser admin, @PathVariable int id) {
        adminService.deletePost(admin, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/comments")
    public PageResponse<CommentRow> comments(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.comments(Math.max(page, 0), clamp(size));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser admin, @PathVariable int id) {
        adminService.deleteComment(admin, id);
        return ResponseEntity.noContent().build();
    }

    public record SeasonScheduleRequest(LocalDateTime splitStartsAt, LocalDateTime endsAt) {
    }

    /** 진행 중인 시즌 일정 바꾸기 (정각 단위). 스케줄러가 다음 정각 실행에 반영한다 */
    @PutMapping("/season/schedule")
    public CurrentSeasonDto updateSeasonSchedule(@AuthenticationPrincipal AuthUser admin,
                                                 @RequestBody SeasonScheduleRequest request) {
        return adminSeasonService.updateSchedule(admin, request.splitStartsAt(), request.endsAt());
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditRow> auditLogs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return adminService.auditLogs(Math.max(page, 0), clamp(size));
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
