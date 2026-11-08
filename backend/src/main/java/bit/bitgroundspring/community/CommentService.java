package bit.bitgroundspring.community;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.community.dto.CommentRequestDto;
import bit.bitgroundspring.community.dto.CommentResponseDto;
import bit.bitgroundspring.notification.Message;
import bit.bitgroundspring.notification.Notification;
import bit.bitgroundspring.notification.NotificationRepository;
import bit.bitgroundspring.notification.NotificationService;
import bit.bitgroundspring.notification.NotificationSettingService;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.user.Role;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final InquiryNotifier inquiryNotifier;
    private final NotificationSettingService notificationSettingService;

    // 알림 문구에 넣는 댓글·제목 길이 (notifications.message는 255자)
    private static final int SNIPPET_LENGTH = 40;
    private static final int TITLE_LENGTH = 30;

    /** 댓글 저장. 볼 수 없는 글(남의 문의, 삭제된 글)에는 달 수 없다 */
    @Transactional
    public void save(Integer userId, CommentRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        boolean admin = user.getRole() == Role.ROLE_ADMIN;
        Post post = PostAccess.requireReadable(postRepository.findById(dto.getPostId()).orElse(null), userId, admin);

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
        if (post.getCategory().isPrivate()) {
            // 문의: 관리자가 처음 답하면 답변 완료로 표시하고, 작성자·관리자 사이에만 알린다
            if (admin && post.getAnsweredAt() == null) {
                post.setAnsweredAt(LocalDateTime.now());
            }
            inquiryNotifier.onComment(post, user, admin, dto.getContent());
        } else {
            notifyNewComment(post, comment.getParent(), user, dto.getContent());
        }
    }

    /**
     * 게시글 작성자에게 '댓글', 답글이면 원댓글 작성자에게 '답글' 알림. 자기 글·댓글에 단 것은 알리지 않고,
     * 게시글 작성자가 원댓글 작성자이기도 하면 답글 알림 하나만 보낸다.
     * 알림은 댓글과 같은 트랜잭션에 저장하고, 실시간 전송은 커밋된 뒤에 한다 (받은 화면이 곧바로 개수를 다시 읽으므로)
     */
    private void notifyNewComment(Post post, Comment parent, User writer, String content) {
        String snippet = "“" + shorten(content, SNIPPET_LENGTH) + "”";
        Map<Integer, String> messages = new LinkedHashMap<>();
        if (parent != null) {
            messages.put(parent.getUser().getId(), writer.getName() + "님이 내 댓글에 답글을 남겼어요.\n" + snippet);
        }
        messages.putIfAbsent(post.getUser().getId(),
                writer.getName() + "님이 내 게시글에 댓글을 남겼어요.\n[" + shorten(post.getTitle(), TITLE_LENGTH) + "] " + snippet);
        messages.remove(writer.getId());
        messages.keySet().retainAll(notificationSettingService.receivers(Message.COMMENT, messages.keySet())); // 댓글 알림을 끈 사람 빼기
        if (messages.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationRepository.saveAll(messages.entrySet().stream()
                .map(e -> Notification.builder()
                        .user(userRepository.getReferenceById(e.getKey()))
                        .messageType(Message.COMMENT)
                        .message(e.getValue())
                        .postId(post.getId())
                        .build())
                .toList());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                saved.forEach(notificationService::push);
            }
        });
    }

    private static String shorten(String text, int max) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
    /**
     * 게시글의 댓글 트리
     * 댓글 전체를 작성자와 함께 쿼리 한 번으로 가져와 메모리에서 부모-자식으로 엮는다.
     * (기존: 댓글마다 작성자·자식 댓글을 지연 로딩하며 재귀로 순회해 댓글 수만큼 쿼리가 나감)
     */
    @Transactional(readOnly = true)
    public List<CommentResponseDto> getCommentsByPostId(Integer postId, AuthUser viewer) {
        requireReadable(postId, viewer);
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

    private void requireReadable(Integer postId, AuthUser viewer) {
        PostAccess.requireReadable(postRepository.findById(postId).orElse(null), viewer.id(), viewer.admin());
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
