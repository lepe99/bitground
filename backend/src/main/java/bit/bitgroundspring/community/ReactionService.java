package bit.bitgroundspring.community;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    /**
     * 좋아요·싫어요 토글. 문의 글과 그 댓글에는 반응할 수 없다 (볼 수 없는 문의면 없는 글로 응답)
     */
    @Transactional
    public void toggleReaction(Long userId, boolean admin, ReactionTargetType type, Long targetId, boolean liked) {
        Post post = type == ReactionTargetType.POST
                ? postRepository.findById(targetId.intValue()).orElse(null)
                : commentRepository.findById(targetId.intValue()).map(Comment::getPost).orElse(null);
        if (post == null) {
            throw new BusinessException(type == ReactionTargetType.POST ? ErrorCode.POST_NOT_FOUND : ErrorCode.COMMENT_NOT_FOUND);
        }
        PostAccess.requireReadable(post, userId.intValue(), admin);
        if (post.getCategory().isPrivate()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문의 글에는 반응할 수 없어요.");
        }
        Reaction existing = reactionRepository
                .findByUserIdAndTargetTypeAndTargetIdAndLiked(userId.intValue(), type, targetId, liked)
                .orElse(null);

        if (existing != null) {
            // 같은 반응 → 취소
            reactionRepository.delete(existing);
            updateCount(type, targetId, liked, -1);
        } else {
            // 반대 반응 → 등록
            Reaction newReaction = Reaction.builder()
                    .user(User.builder().id(userId.intValue()).build())
                    .targetType(type)
                    .targetId(targetId)
                    .liked(liked)
                    .build();
            reactionRepository.save(newReaction);
            updateCount(type, targetId, liked, 1);
        }
    }

    // 엔티티를 읽어 +1 해서 저장하지 않고 UPDATE ... SET likes = likes + :delta 로 원자적으로 증감
    private void updateCount(ReactionTargetType type, Long targetId, boolean liked, int delta) {
        int id = targetId.intValue();
        int updated;
        if (type == ReactionTargetType.POST) {
            updated = liked ? postRepository.addLikes(id, delta) : postRepository.addDislikes(id, delta);
            if (updated == 0) throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        } else {
            updated = liked ? commentRepository.addLikes(id, delta) : commentRepository.addDislikes(id, delta);
            if (updated == 0) throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }
}
