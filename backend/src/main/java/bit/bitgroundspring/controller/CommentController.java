package bit.bitgroundspring.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import bit.bitgroundspring.dto.request.CommentRequestDto;
import bit.bitgroundspring.dto.response.CommentResponseDto;
import bit.bitgroundspring.service.CommentService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;


    // 댓글 등록
    @PostMapping
    public ResponseEntity<Void> createComment(@AuthenticationPrincipal AuthUser authUser,
                                              @Valid @RequestBody CommentRequestDto dto) {
        commentService.save(authUser.id(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 특정 게시글의 댓글 전체 조회 (PostDetail 페이지에서 사용)
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentResponseDto>> getCommentsByPostId(@AuthenticationPrincipal AuthUser authUser,
                                                                        @PathVariable Integer postId) {
        List<CommentResponseDto> comments = commentService.getCommentsByPostId(postId, authUser);
        return ResponseEntity.ok(comments);
    }

    // 댓글 삭제
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Integer commentId) {
        commentService.deleteComment(commentId, authUser);
        return ResponseEntity.noContent().build();
    }
}
