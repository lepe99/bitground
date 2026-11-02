package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.PostRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public void toggleReaction(Long userId, ReactionTargetType type, Long targetId, boolean liked) {
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
