// src/main/java/bit/bitgroundspring/controller/AiAdviceController.java
package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.AiAdviceDto;
import bit.bitgroundspring.service.AiAdviceService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/mypage/ai-advice") // AI 조언 관련 요청을 처리하는 기본 경로
@RequiredArgsConstructor
public class AiAdviceController {

    private final AiAdviceService aiAdviceService;
    private final UserService userService; // userId를 얻기 위해 UserService가 필요합니다.

    /**
     * 특정 사용자 및 시즌에 대한 AI 투자 조언을 생성하거나 조회합니다.
     * 프론트엔드에서 selectedSeason ID를 쿼리 파라미터로 받아 해당 시즌의 AI 조언을 반환합니다.
     * @param jwtToken 인증 토큰 (쿠키에서 추출)
     * @param seasonId 조회할 시즌의 ID
     * @return AiAdviceDto 객체를 포함하는 ResponseEntity. 조언이 없으면 404를 반환합니다.
     */
    @GetMapping // GET /ai-advice?seasonId={seasonId} 요청을 처리합니다.
    public AiAdviceDto getAiAdviceForSeason(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam("seasonId") Integer seasonId) {

        // 1. JWT 토큰에서 사용자 정보 추출
        Integer userId = userService.getActiveUser(authUser.id()).getId();
        AiAdviceDto aiAdvice = aiAdviceService.generateOrGetAiAdviceForUserAndSeason(userId, seasonId);
        if (aiAdvice == null) {
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE);
        }
        return aiAdvice;
    }
}
