package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.AnswerDto;
import bit.bitgroundspring.dto.InquireRequestDto;
import bit.bitgroundspring.dto.InquireResponseDto;
import bit.bitgroundspring.dto.response.Message;
import bit.bitgroundspring.dto.response.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.Inquiry;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.InquireRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.util.UserSseEmitters;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InquireService {
    private final InquireRepository inquireRepository;
    private final UserRepository userRepository;
    private final UserSseEmitters userSseEmitters;

    public void createInquiry(InquireRequestDto dto) {
        User user = userRepository.findById(dto.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException());

        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .title(dto.getTitle())
                .content(dto.getContent())
                .isAnswered(false)
                .build();
        inquireRepository.save(inquiry);
    }

    public void deleteInquiry(Integer id) {
        if (!inquireRepository.existsById(id)) {
            throw new IllegalArgumentException("해당 문의사항이 존재하지 않습니다.");
        }

        inquireRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<InquireResponseDto> searchInquiries(String keyword, Pageable pageable) {
        Page<Inquiry> inquiryPage;

        if (keyword == null || keyword.trim().isEmpty()) {
            inquiryPage = inquireRepository.findAllByOrderByCreatedAtDesc(pageable); // @EntityGraph 적용됨
        } else {
            inquiryPage = inquireRepository.findByTitleContainingIgnoreCase(
                    keyword, pageable); // @EntityGraph 적용됨
        }

        return inquiryPage.map(InquireResponseDto::new); // DTO 변환 (Lazy-safe)
    }

    // InquireService.java
    @Transactional
    public void updateAnswer(Integer inquiryId, AnswerDto dto, String adminUsername) {
        Inquiry inquiry = inquireRepository.findById(inquiryId)
                .orElseThrow(() -> new EntityNotFoundException("Inquiry not found"));

        inquiry.setAnswer(dto.getContent());
        inquiry.setAnswerWriter(adminUsername);
        inquiry.setAnsweredAt(LocalDateTime.now());
        inquiry.setIsAnswered(true);
        
        // SSE 알림 전송
        String title = inquiry.getTitle();
        Integer userId = inquiry.getUser().getId();
        Map<String, Object> data = Map.of(
                "title", title
        );
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.INQUIRY_UPDATE)
                .data(data)
                .build();
        userSseEmitters.sendToUser(userId, notificationResponse);
    }
}
