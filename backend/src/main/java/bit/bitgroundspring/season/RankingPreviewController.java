package bit.bitgroundspring.season;

import bit.bitgroundspring.season.dto.PublicRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class RankingPreviewController {

    private final RankingPreviewService rankingPreviewService;

    @GetMapping("/ranking")
    public PublicRankingResponse getPublicRankingPreview() {
        return rankingPreviewService.getPublicRankingPreview();
    }
}