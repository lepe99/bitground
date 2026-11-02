package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.NoticeRequestDto;
import bit.bitgroundspring.dto.NoticeResponseDto;
import bit.bitgroundspring.entity.Notice;
import bit.bitgroundspring.service.NoticeService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal AuthUser authUser,
                                    @RequestBody NoticeRequestDto dto) {
        Notice saved = noticeService.createNotice(authUser.id(), dto);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotices(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<NoticeResponseDto> page = noticeService.searchNotices(keyword, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("number", page.getNumber());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Integer id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.ok().build();
    }
}