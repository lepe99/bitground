package bit.bitgroundspring.community;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시글 읽기 권한 (한 곳에서 판단). 공개 분류는 로그인한 누구나, 문의는 작성자와 관리자만.
 * 게시글 상세·댓글 목록·댓글 수·댓글 작성·반응이 모두 이 판단을 거친다.
 * 볼 수 없는 문의는 '없는 글'로 응답해 존재 자체를 드러내지 않는다
 */
@Component
@RequiredArgsConstructor
public class PostAccess {

    private final PostRepository postRepository;

    public static boolean canRead(Post post, Integer userId, boolean admin) {
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            return false;
        }
        if (!post.getCategory().isPrivate()) {
            return true;
        }
        return admin || post.getUser().getId().equals(userId);
    }

    /** 볼 수 있는 게시글. 없거나 볼 수 없으면 POST_NOT_FOUND */
    public Post requireReadable(Integer postId, AuthUser viewer) {
        Post post = postRepository.findById(postId).orElse(null);
        return requireReadable(post, viewer.id(), viewer.admin());
    }

    public static Post requireReadable(Post post, Integer userId, boolean admin) {
        if (!canRead(post, userId, admin)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
