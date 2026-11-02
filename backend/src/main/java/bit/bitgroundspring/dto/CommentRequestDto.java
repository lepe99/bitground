package bit.bitgroundspring.dto;

import lombok.Data;

@Data
public class CommentRequestDto {
    private Integer postId;
    private Integer userId;
    private String content;
    private Integer parentId; // null이면 일반 댓글
}
