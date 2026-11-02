package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final BoardRepository boardRepository;
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

    private void updateCount(ReactionTargetType type, Long targetId, boolean liked, int delta) {
        if (type == ReactionTargetType.POST) {
            Post post = boardRepository.findById(targetId.intValue()).orElseThrow();
            if (liked) post.setLikes(post.getLikes() + delta);
            else post.setDislikes(post.getDislikes() + delta);
            boardRepository.save(post);
        } else {
            Comment comment = commentRepository.findById(targetId.intValue()).orElseThrow();
            if (liked) comment.setLikes(comment.getLikes() + delta);
            else comment.setDislikes(comment.getDislikes() + delta);
            commentRepository.save(comment);
        }
    }
}