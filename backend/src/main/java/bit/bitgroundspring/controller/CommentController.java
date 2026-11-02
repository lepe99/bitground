package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.CommentRequestDto;
import bit.bitgroundspring.dto.CommentResponseDto;
import bit.bitgroundspring.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;


    // 댓글 등록
    @PostMapping
    public ResponseEntity<?> createComment(@RequestBody CommentRequestDto dto) {
        commentService.save(dto);
        return ResponseEntity.ok().build();
    }

    // 특정 게시글의 댓글 전체 조회 (PostDetail 페이지에서 사용)
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentResponseDto>> getCommentsByPostId(@PathVariable Integer postId) {
        List<CommentResponseDto> comments = commentService.getCommentsByPostId(postId);
        return ResponseEntity.ok(comments);
    }

    // 댓글 삭제
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Integer commentId,
            @RequestParam Integer userId) {
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<Void> likeComment(@PathVariable Integer commentId) {
        commentService.likeComment(commentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{commentId}/dislike")
    public ResponseEntity<Void> dislikeComment(@PathVariable Integer commentId) {
        commentService.dislikeComment(commentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/post/{postId}/count")
    public ResponseEntity<Long> getCommentCount(@PathVariable Integer postId) {
        long count = commentService.countByPostId(postId);
        return ResponseEntity.ok(count);
    }
}
