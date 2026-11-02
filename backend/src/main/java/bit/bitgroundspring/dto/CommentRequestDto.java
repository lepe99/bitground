package bit.bitgroundspring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentRequestDto {
    @NotNull(message = "게시글 ID는 필수입니다.")
    private Integer postId;
    @NotBlank(message = "댓글 내용을 입력해주세요.")
    private String content;
    private Integer parentId; // null이면 일반 댓글
}
