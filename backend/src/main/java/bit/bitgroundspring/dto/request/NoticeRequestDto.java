package bit.bitgroundspring.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoticeRequestDto {
    @NotBlank(message = "제목을 입력해주세요.")
    private String title;
    @NotBlank(message = "내용을 입력해주세요.")
    private String content;
    // 등록할 때 전체 사용자에게 알림을 보낼지 (비우면 보냄). 수정할 때는 쓰지 않는다
    private Boolean notify;
}
