package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.NoticeRequestDto;
import bit.bitgroundspring.dto.NoticeResponseDto;
import bit.bitgroundspring.entity.Notice;
import bit.bitgroundspring.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody NoticeRequestDto dto) {
        Notice saved = noticeService.createNotice(dto);
        return ResponseEntity.ok(saved);
    }

//    @GetMapping
//    public Page<NoticeResponseDto> getNotices(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size) {
//        return noticeService.getPagedNoticeDtos(page, size);
//    }
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