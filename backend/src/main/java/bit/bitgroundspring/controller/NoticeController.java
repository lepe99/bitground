package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.dto.response.IdResponse;
import jakarta.validation.Valid;
import java.net.URI;
import bit.bitgroundspring.dto.NoticeRequestDto;
import bit.bitgroundspring.dto.NoticeResponseDto;
import bit.bitgroundspring.entity.Notice;
import bit.bitgroundspring.service.NoticeService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @PostMapping
    public ResponseEntity<IdResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                             @Valid @RequestBody NoticeRequestDto dto) {
        Notice saved = noticeService.createNotice(authUser.id(), dto);
        return ResponseEntity.created(URI.create("/notices/" + saved.getId())).body(new IdResponse(saved.getId()));
    }

    @GetMapping
    public PageResponse<NoticeResponseDto> getNotices(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return PageResponse.of(noticeService.searchNotices(keyword, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Integer id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }
}