package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.dto.PageResponseDto;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.BoardService;
import bit.bitgroundspring.service.RankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@Slf4j
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class BoardController {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final BoardService boardService;
    private final CommentRepository commentRepository;
    private final RankService rankService;


    // ✅ 게시글 등록 (FormData 기반)
    @PostMapping("/form")
    public ResponseEntity<?> createPost(@AuthenticationPrincipal AuthUser authUser, @RequestBody Post request) {
        try {
            // 작성자는 요청 본문이 아니라 로그인 토큰 기준으로 정한다
            User user = userRepository.findById(authUser.id())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // 클라이언트가 보낸 값 중 제목/내용/카테고리만 사용 (조회수·좋아요 등 임의 설정 방지)
            Post post = Post.builder()
                    .user(user)
                    .tier(user.getTier())
                    .category(request.getCategory())
                    .title(request.getTitle())
                    .content(request.getContent())
                    .build();

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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        Post post = boardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글이 없습니다."));

        // 작성자 본인 또는 관리자만 삭제 가능
        if (!post.getUser().getId().equals(authUser.id()) && !authUser.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "삭제 권한이 없습니다.");
        }

        post.setIsDeleted(true);
        boardRepository.save(post);

        return ResponseEntity.ok("게시글이 삭제되었습니다.");
    }


}

