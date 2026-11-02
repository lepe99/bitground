package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.dto.request.PostCreateRequest;
import bit.bitgroundspring.dto.response.IdResponse;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.BoardService;
import bit.bitgroundspring.service.RankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class BoardController {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final BoardService boardService;
    private final CommentRepository commentRepository;
    private final RankService rankService;

    // 게시글 등록
    @PostMapping("/form")
    public ResponseEntity<IdResponse> createPost(@AuthenticationPrincipal AuthUser authUser,
                                                 @Valid @RequestBody PostCreateRequest request) {
        // 작성자는 요청 본문이 아니라 로그인 토큰 기준으로 정한다
        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = boardRepository.save(Post.builder()
                .user(user)
                .tier(user.getTier())
                .category(request.category())
                .title(request.title())
                .content(request.content())
                .build());

        return ResponseEntity.created(URI.create("/posts/" + post.getId())).body(new IdResponse(post.getId()));
    }

    // 게시글 목록
    @GetMapping("/list")
    public PageResponse<BoardDto> getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "latest") String sort) {
        Sort sorting = switch (sort) {
            case "oldest" -> Sort.by("created_at").ascending();
            case "popular" -> Sort.by("likes").descending();
            case "views" -> Sort.by("views").descending();
            default -> Sort.by("created_at").descending();
        };
        Pageable pageable = PageRequest.of(page, size, sorting);
        return PageResponse.of(boardService.getBoardDtos(category, pageable));
    }

    // 게시글 상세보기
    @GetMapping("/{id}")
    public BoardDto getPostDetail(
            @PathVariable Integer id,
            @RequestParam(value = "forceViewCount", required = false) Boolean forceViewCount) {
        Post post = boardRepository.findWithUserById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
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

        return dto;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        Post post = boardRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 작성자 본인 또는 관리자만 삭제 가능
        if (!post.getUser().getId().equals(authUser.id()) && !authUser.admin()) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }

        post.setIsDeleted(true);
        boardRepository.save(post);

        return ResponseEntity.noContent().build();
    }
}
