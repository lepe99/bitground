package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.dto.PageResponseDto;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.naver.NcpObjectStorageService;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.BoardService;
import bit.bitgroundspring.service.RankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class BoardController {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final NcpObjectStorageService objectStorageService;
    private final BoardService boardService;
    private final CommentRepository commentRepository;
    private final RankService rankService;


    @Value("${ncp.bucket}")
    private String bucketName;

    // ✅ Quill 에디터용 이미지 업로드
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String directory = "posts"; // 저장 경로 (선택)
            String fileName = objectStorageService.uploadFile(bucketName, directory, file);
            String fileUrl = "https://bitground.kr.object.ncloudstorage.com/posts/" + fileName;

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("이미지 업로드 실패");
        }
    }

    // ✅ 게시글 등록 (FormData 기반)
    @PostMapping("/form")
    public ResponseEntity<?> createPost(@RequestBody Post post) {
        try {
            if (post.getUser() == null || post.getUser().getId() == null) {
                return ResponseEntity.badRequest().body("userId 누락");
            }

            // DB에서 영속 객체로 교체
            User user = userRepository.findById(post.getUser().getId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            post.setUser(user);
            post.setTier(user.getTier());

            boardRepository.save(post);
            return ResponseEntity.ok("글 등록 성공");

        } catch (Exception e) {
            log.error("글 등록 중 오류 발생", e);
            return ResponseEntity.status(500).body("글 등록 실패");
        }
    }

    // 게시글 목록 출력
    @GetMapping("/list")
    public ResponseEntity<?> getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort)
    {
        Sort sorting;
        switch (sort) {
            case "oldest":
                sorting = Sort.by("created_at").ascending();
                break;
            case "popular":
                sorting = Sort.by("likes").descending();
                break;
            case "views":
                sorting = Sort.by("views").descending();
                break;
            default:
                sorting = Sort.by("created_at").descending();
        }
        Pageable pageable = PageRequest.of(page, size, sorting);
        // ✅ 페이징 결과를 PageImpl 그대로 반환 시 Jackson 경고 발생 가능성 있음
        // → PageResponseDto로 감싸서 JSON 구조를 안정적으로 유지
        Page<BoardDto> posts = boardService.getBoardDtos(category, pageable);
        PageResponseDto<BoardDto> response = new PageResponseDto<>(posts);
        return ResponseEntity.ok(response);
    }

    // 게시글 상세보기
    @GetMapping("/{id}")
    public ResponseEntity<?> getPostDetail(
            @PathVariable Integer id,
            @RequestParam(value = "forceViewCount", required = false) Boolean forceViewCount) {
        Post post = boardRepository.findWithUserById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));
        if (Boolean.TRUE.equals(forceViewCount)) {
            post.setViews(post.getViews() + 1);
            boardRepository.save(post); // 무조건 조회수 증가
        }


        boolean hasImage = post.getContent() != null && post.getContent().contains("<img");

        Long commentCount = commentRepository.countByPostId(post.getId());

        BoardDto dto = new BoardDto(
                post.getId(),
                post.getUser().getId(),
                post.getUser().getName(),
                post.getUser().getProfileImage(),
                post.getTitle(),
                post.getContent(),
                post.getTier(),
                post.getLikes(),
                post.getDislikes(),
                post.getIsDeleted(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getCategory().name(),
                post.getViews(),
                commentCount,
                hasImage
        );
        // 나머지는 setter로 추가
        RankDetailResponse tierInfo = rankService.getUserTierDetails(post.getUser().getId());
        dto.setHighestTier(tierInfo.getHighestTier());
        dto.setPastSeasonTiers(tierInfo.getPastSeasonTiers());

        return ResponseEntity.ok(dto);
    }

    //좋아요 버튼 기능
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likePost(@PathVariable Integer id) {
        Post post = boardRepository.findById(id).orElseThrow();
        post.setLikes(post.getLikes() + 1);
        boardRepository.save(post);
        return ResponseEntity.ok().body(post.getLikes());
    }

    //싫어요 버튼 기능
    @PostMapping("/{id}/dislike")
    public ResponseEntity<?> dislikePost(@PathVariable Integer id) {
        Post post = boardRepository.findById(id).orElseThrow();
        post.setDislikes(post.getDislikes() + 1);
        boardRepository.save(post);
        return ResponseEntity.ok().body(post.getDislikes());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Integer id) {
        Post post = boardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        post.setIsDeleted(true);
        boardRepository.save(post);

        return ResponseEntity.ok("게시글이 삭제되었습니다.");
    }


}

