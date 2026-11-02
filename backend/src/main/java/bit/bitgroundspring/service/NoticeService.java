package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.html.HtmlSanitizer;
import bit.bitgroundspring.dto.request.NoticeRequestDto;
import bit.bitgroundspring.dto.response.NoticeResponseDto;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Notice;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.NoticeRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.service.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final UserSseEmitters userSseEmitters;
    private final HtmlSanitizer htmlSanitizer;

    @Loggable("공지 등록")
    public Notice createNotice(Integer userId, NoticeRequestDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Notice notice = Notice.builder()
                .user(user)
                .title(dto.getTitle())
                .content(htmlSanitizer.sanitize(dto.getContent()))
                .build();
        Notice saved = noticeRepository.save(notice);

        // 저장이 끝난 뒤 사용자들에게 공지 등록 알림 전송 (기존: 저장 전에 알림부터 보냄)
        Map<String, Object> data = Map.of(
                "title", dto.getTitle()
        );
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.NOTICE)
                .data(data)
                .build();
        userSseEmitters.sendToAll(notificationResponse);

        return saved;
    }

//    public Page<NoticeResponseDto> getPagedNoticeDtos(int page, int size) {
//        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
//
//        return noticeRepository.findAll(pageable) // ★ @EntityGraph로 user도 미리 로딩됨
//                .map(notice -> new NoticeResponseDto(
//                        notice.getId(),
//                        notice.getTitle(),
//                        notice.getUser().getName(), // Lazy 로딩 오류 ❌
//                        notice.getContent(),
//                        notice.getCreatedAt(),
//                        notice.getUser().getId()
//                ));
//    }
public Page<NoticeResponseDto> searchNotices(String keyword, Pageable pageable) {
    Page<Notice> noticePage;

    if (keyword == null || keyword.trim().isEmpty()) {
        noticePage = noticeRepository.findAllByOrderByCreatedAtDesc(pageable);
    } else {
        noticePage = noticeRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }

    return noticePage.map(NoticeResponseDto::new); // ✅ DTO 변환
}

    public void deleteNotice(Integer id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }

        noticeRepository.deleteById(id);
    }
}
