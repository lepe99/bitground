package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.request.CommentRequestDto;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Comment;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.Notification;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.CommentRepository;
import bit.bitgroundspring.repository.NotificationRepository;
import bit.bitgroundspring.repository.NotificationSettingRepository;
import bit.bitgroundspring.repository.PostRepository;
import bit.bitgroundspring.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CommentServiceTest {

    private static final User POST_AUTHOR = User.builder().id(1).name("글쓴이").build();
    private static final User COMMENTER = User.builder().id(2).name("댓글러").build();
    private static final User REPLIER = User.builder().id(3).name("답글러").build();

    private CommentRepository commentRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private CommentService commentService;
    private Post post;

    @BeforeEach
    void setUp() {
        commentRepository = mock(CommentRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        PostRepository postRepository = mock(PostRepository.class);
        commentService = new CommentService(commentRepository, postRepository, userRepository, notificationRepository, notificationService,
                mock(InquiryNotifier.class), new NotificationSettingService(mock(NotificationSettingRepository.class))); // 설정 없음: 모두 받음

        post = Post.builder().id(10).title("비트코인 전망").category(Category.CHAT).user(POST_AUTHOR).build();
        when(postRepository.findById(10)).thenReturn(Optional.of(post));
        for (User u : List.of(POST_AUTHOR, COMMENTER, REPLIER)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
            when(userRepository.getReferenceById(u.getId())).thenReturn(u);
        }
        when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        TransactionSynchronizationManager.initSynchronization(); // 실제 트랜잭션 대신 커밋 뒤 작업을 모아 둔다
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Map<Integer, String> notifiedOn(Integer userId, Integer parentId, String content) {
        CommentRequestDto dto = new CommentRequestDto();
        dto.setPostId(10);
        dto.setParentId(parentId);
        dto.setContent(content);
        commentService.save(userId, dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, atMostOnce()).saveAll(captor.capture());
        if (captor.getAllValues().isEmpty()) {
            return Map.of();
        }
        captor.getValue().forEach(n -> {
            assertThat(n.getMessageType()).isEqualTo(Message.COMMENT);
            assertThat(n.getPostId()).isEqualTo(10);
        });
        return captor.getValue().stream().collect(Collectors.toMap(n -> n.getUser().getId(), Notification::getMessage));
    }

    private void parentBy(User author) {
        Comment parent = Comment.builder().id(100).post(post).user(author).content("원댓글").build();
        when(commentRepository.findById(100)).thenReturn(Optional.of(parent));
    }

    @Test
    void 내_게시글에_댓글이_달리면_게시글_작성자에게_알린다() {
        Map<Integer, String> sent = notifiedOn(COMMENTER.getId(), null, "좋은 글이네요");

        assertThat(sent).containsOnlyKeys(POST_AUTHOR.getId());
        assertThat(sent.get(POST_AUTHOR.getId())).isEqualTo("댓글러님이 내 게시글에 댓글을 남겼어요.\n[비트코인 전망] “좋은 글이네요”");
    }

    @Test
    void 답글은_원댓글_작성자와_게시글_작성자에게_알린다() {
        parentBy(COMMENTER);
        Map<Integer, String> sent = notifiedOn(REPLIER.getId(), 100, "저도요");

        assertThat(sent).containsOnlyKeys(COMMENTER.getId(), POST_AUTHOR.getId());
        assertThat(sent.get(COMMENTER.getId())).startsWith("답글러님이 내 댓글에 답글을 남겼어요.");
        assertThat(sent.get(POST_AUTHOR.getId())).startsWith("답글러님이 내 게시글에 댓글을 남겼어요.");
    }

    @Test
    void 게시글_작성자의_댓글에_답글이_달리면_답글_알림_하나만_보낸다() {
        parentBy(POST_AUTHOR);
        Map<Integer, String> sent = notifiedOn(REPLIER.getId(), 100, "답글");

        assertThat(sent).containsOnlyKeys(POST_AUTHOR.getId());
        assertThat(sent.get(POST_AUTHOR.getId())).startsWith("답글러님이 내 댓글에 답글을 남겼어요.");
    }

    @Test
    void 자기_글과_댓글에_단_것은_알리지_않는다() {
        parentBy(POST_AUTHOR);
        assertThat(notifiedOn(POST_AUTHOR.getId(), 100, "제 글입니다")).isEmpty();
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void 긴_댓글은_줄여서_넣고_실시간_전송은_커밋_뒤에_한다() {
        Map<Integer, String> sent = notifiedOn(COMMENTER.getId(), null, "가".repeat(60) + "\n\n끝");

        assertThat(sent.get(POST_AUTHOR.getId())).endsWith("가".repeat(40) + "…”");
        verify(notificationService, never()).push(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(notificationService).push(any());
    }
}
