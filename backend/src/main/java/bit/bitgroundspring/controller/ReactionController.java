package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.ReactionDto;
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
    public ResponseEntity<?> toggleReaction(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody ReactionDto dto) {
        reactionService.toggleReaction(
                authUser.id().longValue(),
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getLiked()
        );
        return ResponseEntity.ok().build();
    }
}
