package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.NoticeRequestDto;
import bit.bitgroundspring.dto.NoticeResponseDto;
import bit.bitgroundspring.dto.response.Message;
import bit.bitgroundspring.dto.response.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Notice;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.NoticeRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.util.UserSseEmitters;
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

    public Notice createNotice(NoticeRequestDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        Notice notice = Notice.builder()
                .user(user)
                .title(dto.getTitle())
                .content(dto.getContent())
                .build();
        
        // 사용자들에게 공지 등록 알림 전송
        Map<String, Object> data = Map.of(
                "title", dto.getTitle()
        );
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.NOTICE)
                .data(data)
                .build();
        userSseEmitters.sendToAll(notificationResponse);

        return noticeRepository.save(notice);
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
            throw new IllegalArgumentException("해당 공지사항이 존재하지 않습니다");
        }

        noticeRepository.deleteById(id);
    }
}
