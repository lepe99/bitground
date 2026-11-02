package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.ReactionDto;
import bit.bitgroundspring.service.ReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    public ResponseEntity<?> toggleReaction(@RequestBody ReactionDto dto) {
        reactionService.toggleReaction(
                dto.getUserId(),
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getLiked()
        );
        return ResponseEntity.ok().build();
    }
}
