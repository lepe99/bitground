package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.dto.request.PostCreateRequest;
import bit.bitgroundspring.dto.response.IdResponse;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // 게시글 등록 (작성자는 요청 본문이 아니라 로그인 토큰 기준)
    @PostMapping("/form")
    public ResponseEntity<IdResponse> createPost(@AuthenticationPrincipal AuthUser authUser,
                                                 @Valid @RequestBody PostCreateRequest request) {
        Post post = boardService.createPost(authUser.id(), request);
        return ResponseEntity.created(URI.create("/posts/" + post.getId())).body(new IdResponse(post.getId()));
    }

    // 게시글 목록 (category 생략 시 전체, 잘못된 값이면 400)
    @GetMapping("/list")
    public PageResponse<BoardDto> getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "latest") String sort) {
        Sort sorting = switch (sort) {
            case "oldest" -> Sort.by("createdAt").ascending();
            case "popular" -> Sort.by("likes").descending();
            case "views" -> Sort.by("views").descending();
            default -> Sort.by("createdAt").descending();
        };
        return PageResponse.of(boardService.getBoardList(category, PageRequest.of(page, Math.min(size, 50), sorting)));
    }

    // 게시글 상세보기 (조회수는 서버가 사용자 기준으로 중복을 걸러 올림)
    @GetMapping("/{id}")
    public BoardDto getPostDetail(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        return boardService.getPostDetail(id, authUser.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        boardService.deletePost(id, authUser);
        return ResponseEntity.noContent().build();
    }
}
