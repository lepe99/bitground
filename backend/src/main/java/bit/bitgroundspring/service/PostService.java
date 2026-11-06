package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.html.HtmlSanitizer;
import bit.bitgroundspring.dto.response.PostDto;
import bit.bitgroundspring.dto.request.PostCreateRequest;
import bit.bitgroundspring.dto.response.RankDetailResponse;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.PostRepository;
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
public class PostService {

    // 같은 사용자가 이 시간 안에 다시 열면 조회수를 올리지 않는다
    private static final Duration VIEW_DEDUP_WINDOW = Duration.ofHours(24);

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final RankService rankService;
    private final HtmlSanitizer htmlSanitizer;
    private final StringRedisTemplate redis;
    private final InquiryNotifier inquiryNotifier;

    /**
     * 게시글 등록 (본문 HTML은 허용 목록으로 정제해 저장).
     * 문의는 유형이 필요하고, 등록되면 관리자에게 알린다. 문의가 아닌 글의 유형은 버린다
     */
    @Transactional
    public Post createPost(Integer userId, PostCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        boolean inquiry = request.category().isPrivate();
        if (inquiry && request.inquiryType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문의 유형을 선택해주세요.");
        }
        String content = htmlSanitizer.sanitize(request.content());

        Post post = postRepository.save(Post.builder()
                .user(user)
                .tier(user.getTier())
                .category(request.category())
                .inquiryType(inquiry ? request.inquiryType() : null)
                .title(request.title())
                .content(content)
                .hasImage(htmlSanitizer.containsImage(content))
                .build());
        if (inquiry) {
            inquiryNotifier.newInquiry(post, user);
        }
        return post;
    }

    /**
     * 게시글 목록 (본문 제외).
     * 전체(category 없음)는 문의를 빼고, 문의 탭은 일반 사용자에게 내 문의만, 관리자에게 모든 문의를 보여 준다.
     * answered(답변 여부)는 문의 탭에서만 쓴다
     */
    @Transactional(readOnly = true)
    public Page<PostDto> getPostList(Category category, Boolean answered, AuthUser viewer, Pageable pageable) {
        boolean inquiryTab = category != null && category.isPrivate();
        Integer ownerId = inquiryTab && !viewer.admin() ? viewer.id() : null;
        return postRepository.findPostList(category, Category.INQUIRY, ownerId, inquiryTab ? answered : null, pageable);
    }

    /**
     * 게시글 상세. 조회수는 같은 사용자 기준 24시간에 한 번만 올린다.
     * (기존: ?forceViewCount=true만 붙이면 누구나 무한히 올릴 수 있었음)
     */
    @Transactional
    public PostDto getPostDetail(Integer postId, AuthUser viewer) {
        // 볼 수 없는 문의는 없는 글로 응답한다 (조회수도 올리지 않음)
        Post post = PostAccess.requireReadable(postRepository.findWithUserById(postId).orElse(null),
                viewer.id(), viewer.admin());
        // 문의는 조회수를 쓰지 않는다. 조회수는 UPDATE로 원자적으로 올리고, 응답에는 방금 올린 값을 더해 보여 준다
        boolean counted = !post.getCategory().isPrivate() && countView(postId, viewer.id());
        if (counted) {
            postRepository.incrementViews(postId);
        }

        PostDto dto = new PostDto(
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
        dto.setInquiry(post.getInquiryType(), post.getAnsweredAt());
        if (counted) {
            dto.setViews(dto.getViews() + 1);
        }
        RankDetailResponse tierInfo = rankService.getUserTierDetails(post.getUser().getId());
        dto.setHighestTier(tierInfo.getHighestTier());
        dto.setPastSeasonTiers(tierInfo.getPastSeasonTiers());
        return dto;
    }

    /** 게시글 삭제 (작성자 본인 또는 관리자, 소프트 삭제) */
    @Transactional
    public void deletePost(Integer postId, AuthUser authUser) {
        Post post = postRepository.findById(postId)
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
