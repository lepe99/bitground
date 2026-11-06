package bit.bitgroundspring.controller;

import jakarta.validation.Valid;
import bit.bitgroundspring.dto.request.ReactionDto;
import bit.bitgroundspring.service.ReactionService;
import bit.bitgroundspring.security.token.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    public ResponseEntity<Void> toggleReaction(@AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody ReactionDto dto) {
        reactionService.toggleReaction(
                authUser.id().longValue(),
                authUser.admin(),
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getLiked()
        );
        return ResponseEntity.noContent().build();
    }
}
