package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.CommentRequestDto;
import bit.bitgroundspring.dto.CommentResponseDto;
import bit.bitgroundspring.entity.Comment;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
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
    public void save(CommentRequestDto dto) {
        Post post = boardRepository.findById(dto.getPostId())
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다"));
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다"));

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(dto.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if(dto.getParentId() != null) {
            Comment parent = commentRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("부모 댓글이 존재하지 않습니다"));
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
    public void deleteComment(Integer commentId, Integer userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글 없음"));

        if (!comment.getUser().getId().equals(userId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }

        // 자식 댓글 먼저 삭제
        if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
            for (Comment child : comment.getChildren()) {
                commentRepository.delete(child);
            }
        }

        commentRepository.delete(comment); // 부모 댓글 삭제
    }

    @Transactional
    public void likeComment(Integer commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));
        comment.setLikes(comment.getLikes() + 1);
    }

    @Transactional
    public void dislikeComment(Integer commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));
        comment.setDislikes(comment.getDislikes() + 1);
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
