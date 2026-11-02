// src/main/java/bit/bitgroundspring/controller/AiAdviceController.java
package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.AiAdviceDto;
import bit.bitgroundspring.dto.UserDto;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.security.oauth2.AuthService;
import bit.bitgroundspring.service.AiAdviceService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/mypage/ai-advice") // AI 조언 관련 요청을 처리하는 기본 경로
@RequiredArgsConstructor
public class AiAdviceController {

    private final AiAdviceService aiAdviceService;
    private final AuthService authService;
    private final UserService userService; // userId를 얻기 위해 UserService가 필요합니다.

    /**
     * 특정 사용자 및 시즌에 대한 AI 투자 조언을 생성하거나 조회합니다.
     * 프론트엔드에서 selectedSeason ID를 쿼리 파라미터로 받아 해당 시즌의 AI 조언을 반환합니다.
     * @param jwtToken 인증 토큰 (쿠키에서 추출)
     * @param seasonId 조회할 시즌의 ID
     * @return AiAdviceDto 객체를 포함하는 ResponseEntity. 조언이 없으면 404를 반환합니다.
     */
    @GetMapping // GET /ai-advice?seasonId={seasonId} 요청을 처리합니다.
    public ResponseEntity<AiAdviceDto> getAiAdviceForSeason(
            @CookieValue(name = "jwt_token", required = false) String jwtToken,
            @RequestParam("seasonId") Integer seasonId) {

        // 1. JWT 토큰에서 사용자 정보 추출
        UserDto userDto = authService.getUserInfoFromToken(jwtToken);

        // 2. 추출된 소셜 ID를 사용하여 실제 User 엔티티를 조회하고 userId를 얻습니다.
        Optional<User> userOpt = userService.getUserBySocialId(userDto.getProvider(), userDto.getProviderId());
        if (userOpt.isEmpty()) {
            // 사용자 정보가 데이터베이스에 없으면 404 Not Found를 반환합니다.
            return ResponseEntity.status(404).body(null);
        }
        Integer userId = userOpt.get().getId();

        // 3. AiAdviceService를 통해 특정 사용자 및 시즌에 대한 AI 조언을 생성 또는 조회합니다.
        AiAdviceDto aiAdvice;
        try {
            aiAdvice = aiAdviceService.generateOrGetAiAdviceForUserAndSeason(userId, seasonId);
        } catch (Exception e) {
            // AI 조언 생성/조회 중 예외 발생 시 500 에러 반환
            return ResponseEntity.status(500).body(null);
        }

        if (aiAdvice == null) {
            // AI 조언이 생성되지 않았거나 조회되지 않은 경우 404 반환
            return ResponseEntity.status(404).body(null);
        }

        // 4. 성공적으로 조회 또는 생성된 AI 조언 반환
        return ResponseEntity.ok(aiAdvice);
    }
}
