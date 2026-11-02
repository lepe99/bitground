package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.html.HtmlSanitizer;
import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.dto.request.PostCreateRequest;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class BoardService {

    // 같은 사용자가 이 시간 안에 다시 열면 조회수를 올리지 않는다
    private static final Duration VIEW_DEDUP_WINDOW = Duration.ofHours(24);

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final RankService rankService;
    private final HtmlSanitizer htmlSanitizer;
    private final StringRedisTemplate redis;

    /** 게시글 등록 (본문 HTML은 허용 목록으로 정제해 저장) */
    @Transactional
    public Post createPost(Integer userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String content = htmlSanitizer.sanitize(request.content());

        return boardRepository.save(Post.builder()
                .user(user)
                .tier(user.getTier())
                .category(request.category())
                .title(request.title())
                .content(content)
                .hasImage(htmlSanitizer.containsImage(content))
                .build());
    }

    /** 게시글 목록 (본문 제외) */
    @Transactional(readOnly = true)
    public Page<BoardDto> getBoardList(Category category, Pageable pageable) {
        return boardRepository.findBoardList(category, pageable);
    }

    /**
     * 게시글 상세. 조회수는 같은 사용자 기준 24시간에 한 번만 올린다.
     * (기존: ?forceViewCount=true만 붙이면 누구나 무한히 올릴 수 있었음)
     */
    @Transactional
    public BoardDto getPostDetail(Integer postId, Integer viewerId) {
        if (countView(postId, viewerId)) {
            boardRepository.incrementViews(postId);
        }

        Post post = boardRepository.findWithUserById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

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
                commentRepository.countByPostId(post.getId()),
                Boolean.TRUE.equals(post.getHasImage())
        );
        RankDetailResponse tierInfo = rankService.getUserTierDetails(post.getUser().getId());
        dto.setHighestTier(tierInfo.getHighestTier());
        dto.setPastSeasonTiers(tierInfo.getPastSeasonTiers());
        return dto;
    }

    /** 게시글 삭제 (작성자 본인 또는 관리자, 소프트 삭제) */
    @Transactional
    public void deletePost(Integer postId, AuthUser authUser) {
        Post post = boardRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getUser().getId().equals(authUser.id()) && !authUser.admin()) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }
        post.setIsDeleted(true);
    }

    // 처음 보는 (게시글, 사용자) 조합이면 true. Redis SET NX로 기간 내 중복을 막는다
    private boolean countView(Integer postId, Integer viewerId) {
        Boolean first = redis.opsForValue()
                .setIfAbsent("view:post:" + postId + ":" + viewerId, "1", VIEW_DEDUP_WINDOW);
        return Boolean.TRUE.equals(first);
    }
}
