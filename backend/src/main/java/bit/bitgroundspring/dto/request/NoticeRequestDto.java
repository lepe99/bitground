package bit.bitgroundspring.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoticeRequestDto {
    @NotBlank(message = "제목을 입력해주세요.")
    private String title;
    @NotBlank(message = "내용을 입력해주세요.")
    private String content;
}
