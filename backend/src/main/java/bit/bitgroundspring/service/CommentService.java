package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.CommentRequestDto;
import bit.bitgroundspring.dto.CommentResponseDto;
import bit.bitgroundspring.entity.Comment;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    //댓글 저장
    public void save(Integer userId, CommentRequestDto dto) {
        Post post = boardRepository.findById(dto.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(dto.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if(dto.getParentId() != null) {
            Comment parent = commentRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            comment.setParent(parent);
        }

        commentRepository.save(comment);
    }
    @Transactional(readOnly = true)
    public List<CommentResponseDto> getCommentsByPostId(Integer postId) {
        List<Comment> comments = commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtAsc(postId);

        return comments.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteComment(Integer commentId, AuthUser authUser) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        // 작성자 본인 또는 관리자만 삭제 가능
        if (!comment.getUser().getId().equals(authUser.id()) && !authUser.admin()) {
            throw new BusinessException(ErrorCode.NOT_AUTHOR);
        }

        // 자식 댓글 먼저 삭제
        if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
            for (Comment child : comment.getChildren()) {
                commentRepository.delete(child);
            }
        }

        commentRepository.delete(comment); // 부모 댓글 삭제
    }

    public long countByPostId(Integer postId) {
        return commentRepository.countByPostId(postId);
    }

    private CommentResponseDto toDto(Comment comment) {
        return CommentResponseDto.builder()
                .id(comment.getId())
                .userId(comment.getUser().getId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .content(comment.getContent())
                .userName(comment.getUser().getName())
                .userTier(comment.getUser().getTier()) // ✅ 추가
                .profileImage(comment.getUser().getProfileImage()) // ✅ 추가
                .isDeleted(comment.getIsDeleted())
                .createdAt(comment.getCreatedAt())
                .likes(comment.getLikes())
                .dislikes(comment.getDislikes())
                .children(comment.getChildren().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList()))
                .build();
    }
}
