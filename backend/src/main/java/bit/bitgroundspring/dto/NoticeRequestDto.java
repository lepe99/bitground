package bit.bitgroundspring.dto;

import lombok.Data;

@Data
public class NoticeRequestDto {
    private String title;
    private String content;
    private Integer userId;
}
