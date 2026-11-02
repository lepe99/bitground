package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.Inquiry;
import lombok.Data;

// InquireResponseDto.java
@Data
public class InquireResponseDto {
    private Integer id;
    private String title;
    private String writer;
    private String content;
    private String createdAt;
    private String answer;
    private Integer writerId;



    public InquireResponseDto(Inquiry inquiry) {
        this.id = inquiry.getId();
        this.title = inquiry.getTitle();
        this.writer = inquiry.getUser().getName();
        this.content = inquiry.getContent();
        this.createdAt = inquiry.getCreatedAt().toString();
        this.answer = inquiry.getAnswer();
        this.writerId = inquiry.getUser().getId();
    }
}
