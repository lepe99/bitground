package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.AnswerDto;
import bit.bitgroundspring.dto.InquireRequestDto;
import bit.bitgroundspring.dto.InquireResponseDto;
import bit.bitgroundspring.naver.NcpObjectStorageService;
import bit.bitgroundspring.service.InquireService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/inquiries")
@RequiredArgsConstructor
public class InquireController {

    private final InquireService inquireService;
    private final NcpObjectStorageService ncpObjectStorageService;

    @Value("${ncp.bucket}")
    private String bucketName;

    // ✅ Quill 에디터용 이미지 업로드
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String directory = "inquiries"; // 저장 경로 (선택)
            String fileName = ncpObjectStorageService.uploadFile(bucketName, directory, file);
            String fileUrl = "https://bitground.kr.object.ncloudstorage.com/inquiries/" + fileName;

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("이미지 업로드 실패");
        }
    }
    @PostMapping
    public ResponseEntity<Void> createInquiry(@RequestBody InquireRequestDto dto) {
        inquireService.createInquiry(dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getInquiries(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<InquireResponseDto> page = inquireService.searchInquiries(keyword, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("number", page.getNumber());

        return ResponseEntity.ok(response);
    }


    @PutMapping("/{id}/answer")
    public ResponseEntity<Void> updateAnswer(@PathVariable Integer id, @RequestBody AnswerDto dto, Principal principal) {
        inquireService.updateAnswer(id, dto, principal.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInquiry(@PathVariable Integer id) {
        inquireService.deleteInquiry(id);
        return ResponseEntity.noContent().build();  // 204
    }
}
