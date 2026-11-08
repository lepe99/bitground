package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.InquiryType;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * 문의 알림. 새 문의·작성자의 추가 질문은 관리자들에게, 관리자 답변은 작성자에게.
 * 알림은 글·댓글과 같은 트랜잭션에 저장하고, 실시간 전송은 커밋된 뒤에 한다
 */
@Component
@RequiredArgsConstructor
public class InquiryNotifier {

    private static final Map<InquiryType, String> TYPE_LABEL = Map.of(
            InquiryType.BUG, "버그 제보", InquiryType.ACCOUNT, "계정·이용",
            InquiryType.SUGGESTION, "건의", InquiryType.ETC, "기타");
    private static final int TITLE_LENGTH = 30;
    private static final int SNIPPET_LENGTH = 40;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    /** 새 문의 → 관리자들 */
    public void newInquiry(Post post, User author) {
        String type = TYPE_LABEL.getOrDefault(post.getInquiryType(), "기타");
        toAdmins(post, author, "새 문의가 등록됐어요. [" + type + "]\n" + shorten(post.getTitle(), TITLE_LENGTH));
    }

    /**
     * 문의에 댓글이 달림. 관리자가 달면 작성자에게 답변 알림, 작성자가 달면(추가 질문) 관리자들에게
     */
    public void onComment(Post post, User commenter, boolean byAdmin, String content) {
        String title = "[" + shorten(post.getTitle(), TITLE_LENGTH) + "] “" + shorten(content, SNIPPET_LENGTH) + "”";
        User author = post.getUser();
        if (byAdmin) {
            if (!author.getId().equals(commenter.getId())
                    && !notificationSettingService.receivers(Message.COMMENT, List.of(author.getId())).isEmpty()) {
                save(List.of(notification(author.getId(), Message.COMMENT, "문의에 답변이 등록됐어요.\n" + title, post)));
            }
        } else if (author.getId().equals(commenter.getId())) {
            toAdmins(post, commenter, "문의에 추가 글이 달렸어요.\n" + title);
        }
    }

    private void toAdmins(Post post, User sender, String message) {
        List<Notification> notifications = userRepository.findAdminIds().stream()
                .filter(id -> !id.equals(sender.getId()))
                .map(id -> notification(id, Message.INQUIRY, message, post))
                .toList();
        save(notifications);
    }

    private Notification notification(Integer userId, Message type, String message, Post post) {
        return Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .messageType(type)
                .message(message)
                .postId(post.getId())
                .build();
    }

    private void save(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        List<Notification> saved = notificationRepository.saveAll(notifications);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    saved.forEach(notificationService::push);
                }
            });
        } else {
            saved.forEach(notificationService::push);
        }
    }

    private static String shorten(String text, int max) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
