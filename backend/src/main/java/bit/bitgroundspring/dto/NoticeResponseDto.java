package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.Notice;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NoticeResponseDto {
    private Integer id;
    private String title;
    private String writer;
    private String content;
    private LocalDateTime createdAt;
    private Integer writerId;

    public NoticeResponseDto(Notice notice) {
        this.id = notice.getId();
        this.title = notice.getTitle();
        this.writer = notice.getUser().getName();
        this.content = notice.getContent();
        this.createdAt = notice.getCreatedAt();
        this.writerId = notice.getUser().getId();
    }
}