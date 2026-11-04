// src/main/java/bit/bitgroundspring/controller/AiAdviceController.java
package bit.bitgroundspring.controller;

import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.response.AiAdviceDto;
import bit.bitgroundspring.service.AiAdviceService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 시즌 리뷰. 시즌 정산 때 일괄로 만들지 않고, 사용자가 화면에서 버튼을 눌러 요청할 때만 만든다 (Gemini 호출 비용)
 * - GET: 이미 만든 리뷰 조회만 (없으면 204)
 * - POST: 리뷰 생성 (이미 있으면 그대로 반환)
 */
@RestController
@RequestMapping("/mypage/ai-advice")
@RequiredArgsConstructor
public class AiAdviceController {

    private final AiAdviceService aiAdviceService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<AiAdviceDto> getAiAdviceForSeason(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("seasonId") Integer seasonId) {
        Integer userId = userService.getActiveUser(authUser.id()).getId();
        AiAdviceDto aiAdvice = aiAdviceService.getAiAdviceForUserAndSeason(userId, seasonId);
        return aiAdvice != null ? ResponseEntity.ok(aiAdvice) : ResponseEntity.noContent().build();
    }

    @PostMapping
    public AiAdviceDto generateAiAdviceForSeason(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("seasonId") Integer seasonId) {
        Integer userId = userService.getActiveUser(authUser.id()).getId();
        return aiAdviceService.generateOrGetAiAdviceForUserAndSeason(userId, seasonId);
    }
}
