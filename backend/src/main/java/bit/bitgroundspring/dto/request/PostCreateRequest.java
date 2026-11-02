package bit.bitgroundspring.dto.request;

import bit.bitgroundspring.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글 작성 요청 (기존: Post 엔티티를 요청 본문으로 직접 받음)
 */
public record PostCreateRequest(
        @NotNull(message = "카테고리를 선택해주세요.") Category category,
        @NotBlank(message = "제목을 입력해주세요.") @Size(max = 255, message = "제목은 255자 이하로 입력해주세요.") String title,
        @NotBlank(message = "내용을 입력해주세요.") String content
) {
}
