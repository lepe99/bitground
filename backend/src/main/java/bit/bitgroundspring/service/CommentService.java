package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.request.CommentRequestDto;
import bit.bitgroundspring.dto.response.CommentResponseDto;
import bit.bitgroundspring.entity.Comment;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.PostRepository;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    //댓글 저장
    @Transactional
    public void save(Integer userId, CommentRequestDto dto) {
        Post post = postRepository.findById(dto.getPostId())
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
            // 다른 게시글의 댓글에 답글을 달 수 없도록
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 게시글의 댓글에만 답글을 달 수 있습니다.");
            }
            comment.setParent(parent);
        }

        commentRepository.save(comment);
    }
    /**
     * 게시글의 댓글 트리
     * 댓글 전체를 작성자와 함께 쿼리 한 번으로 가져와 메모리에서 부모-자식으로 엮는다.
     * (기존: 댓글마다 작성자·자식 댓글을 지연 로딩하며 재귀로 순회해 댓글 수만큼 쿼리가 나감)
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> getCommentsByPostId(Integer postId) {
        List<Comment> comments = commentRepository.findAllWithUserByPostId(postId);

        Map<Integer, CommentResponseDto> byId = new LinkedHashMap<>();
        comments.forEach(c -> byId.put(c.getId(), toDto(c)));

        List<CommentResponseDto> roots = new ArrayList<>();
        for (Comment c : comments) {
            CommentResponseDto dto = byId.get(c.getId());
            // getParent().getId()는 프록시를 초기화하지 않으므로 추가 쿼리가 없다
            CommentResponseDto parent = c.getParent() == null ? null : byId.get(c.getParent().getId());
            if (parent == null) {
                roots.add(dto);
            } else {
                parent.getChildren().add(dto);
            }
        }
        return roots;
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
                .userTier(comment.getUser().getTier())
                .profileImage(comment.getUser().getProfileImage())
                .isDeleted(comment.getIsDeleted())
                .createdAt(comment.getCreatedAt())
                .likes(comment.getLikes())
                .dislikes(comment.getDislikes())
                .children(new ArrayList<>())
                .build();
    }
}
