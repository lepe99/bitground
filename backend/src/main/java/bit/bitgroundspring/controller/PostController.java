package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.response.PageResponse;
import bit.bitgroundspring.dto.response.PostDto;
import bit.bitgroundspring.dto.request.PostCreateRequest;
import bit.bitgroundspring.dto.response.IdResponse;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.PostService;
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
public class PostController {

    private final PostService postService;

    // 게시글 등록 (작성자는 요청 본문이 아니라 로그인 토큰 기준)
    @PostMapping("/form")
    public ResponseEntity<IdResponse> createPost(@AuthenticationPrincipal AuthUser authUser,
                                                 @Valid @RequestBody PostCreateRequest request) {
        Post post = postService.createPost(authUser.id(), request);
        return ResponseEntity.created(URI.create("/posts/" + post.getId())).body(new IdResponse(post.getId()));
    }

    // 게시글 목록 (category 생략 시 문의를 뺀 전체, 잘못된 값이면 400). answered는 문의 탭의 답변 대기(false)·완료(true)
    @GetMapping("/list")
    public PageResponse<PostDto> getPostList(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Boolean answered,
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
        return PageResponse.of(postService.getPostList(category, answered, authUser,
                PageRequest.of(page, Math.min(size, 50), sorting)));
    }

    // 게시글 상세보기 (조회수는 서버가 사용자 기준으로 중복을 걸러 올림)
    @GetMapping("/{id}")
    public PostDto getPostDetail(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        return postService.getPostDetail(id, authUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser authUser, @PathVariable Integer id) {
        postService.deletePost(id, authUser);
        return ResponseEntity.noContent().build();
    }
}
