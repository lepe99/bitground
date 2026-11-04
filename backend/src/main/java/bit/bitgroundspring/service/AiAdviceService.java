// src/main/java/bit/bitgroundspring/service/AiAdviceService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.response.AiAdviceDto;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.dto.SeasonReviewContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import bit.bitgroundspring.entity.AiAdvice;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.AiAdviceRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdviceService {

    private final InvestmentAnalysisService investmentAnalysisService;
    private final GeminiService geminiService;
    private final AiAdviceRepository aiAdviceRepository;
    private final UserRepository userRepository;
    private final SeasonRepository seasonRepository;
    private final ObjectMapper objectMapper;

    /**
     * 특정 사용자 및 특정 시즌에 대한 AI 투자 조언을 생성하고 저장합니다.
     * 시즌 상태가 COMPLETED일 때만 작동하며, 이미 조언이 존재하는 경우 기존 조언을 반환합니다.
     *
     * <p>트랜잭션을 걸지 않는다. Gemini 응답을 기다리는 수십 초 동안 DB 연결을 붙잡지 않도록
     * 조회(투자 요약은 자체 읽기 전용 트랜잭션)와 저장만 각각 짧게 DB를 쓴다.
     *
     * @param userId 조언을 생성할 사용자의 ID
     * @param seasonId 조언을 생성할 시즌의 ID
     * @return 생성되거나 조회된 AiAdviceDto
     */
    @Loggable("AI 조언")
    public AiAdviceDto generateOrGetAiAdviceForUserAndSeason(Integer userId, Integer seasonId) {
        log.info("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason 요청 시작: User ID={}, Season ID={}", userId, seasonId);
        try {
            // userId를 기반으로 User 엔티티 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            log.debug("[AiAdviceService] 사용자 엔티티 조회 완료. User ID={}", userId);

            // Season 엔티티 조회 및 상태 확인
            Season season = seasonRepository.findById(seasonId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
            log.debug("[AiAdviceService] 시즌 엔티티 조회 완료. Season ID={}, Status={}", seasonId, season.getStatus());

            // 시즌의 상태가 COMPLETED인지 확인
            if (season.getStatus() != Status.COMPLETED) {
                log.warn("[AiAdviceService] AI 조언은 COMPLETED 상태의 시즌에 대해서만 생성 또는 조회할 수 있습니다. 현재 시즌 상태: {}. User ID={}, Season ID={}",
                        season.getStatus(), userId, seasonId);
                throw new BusinessException(ErrorCode.SEASON_NOT_COMPLETED);
            }

            // 기존 조언이 있는지 확인 (이미 존재하면 업데이트하지 않고 반환)
            Optional<AiAdvice> existingAdvice = aiAdviceRepository.findByUserAndSeason(user, season);
            if (existingAdvice.isPresent()) {
                log.info("[AiAdviceService] AI 조언이 이미 존재합니다. 기존 조언 반환. User ID={}, Season ID={}, Advice ID={}",
                        userId, seasonId, existingAdvice.get().getId());
                return convertToDto(existingAdvice.get());
            }

            log.info("[AiAdviceService] 기존 AI 조언을 찾을 수 없습니다. 새로운 조언을 생성합니다. User ID={}, Season ID={}", userId, seasonId);

            log.info("[AiAdviceService] InvestmentAnalysisService.getUserInvestmentSummaryForSeason() 호출 중...");
            InvestmentSummaryDto summaryDto = investmentAnalysisService.getUserInvestmentSummaryForSeason(userId, seasonId);
            if (summaryDto == null) {
                log.error("[AiAdviceService] 투자 요약 데이터 생성 실패. getUserInvestmentSummaryForSeason()가 null을 반환했습니다. User ID={}, Season ID={}", userId, seasonId);
                throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, "투자 요약 데이터를 생성할 수 없습니다.");
            }
            // 랭킹에 없으면(그 시즌에 체결한 거래가 없으면) 돌아볼 기록이 없으므로 모델을 부르지 않는다
            if (summaryDto.getFinalRank() == null || summaryDto.getTotalTradeCount() == 0) {
                throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, "이 시즌에는 거래 기록이 없어 리뷰를 만들 수 없어요.");
            }
            log.debug("[AiAdviceService] 투자 요약 데이터 생성 완료: {}", summaryDto);

            // 점수는 정해진 규칙으로 코드가 계산하고, 모델은 그 이유와 회고만 쓴다
            SeasonReviewScorer.Score score = SeasonReviewScorer.score(summaryDto);
            SeasonReviewContent review = geminiService.generateSeasonReview(summaryDto, score)
                    .withScoreBreakdown(score.parts());
            log.info("[AiAdviceService] 시즌 리뷰 생성 완료. User ID={}, Season ID={}, Score={}", userId, seasonId, score.total());

            AiAdvice newAdvice = AiAdvice.builder()
                    .user(user)
                    .season(season)
                    .score(score.total())
                    .advice(objectMapper.writeValueAsString(review))
                    .promptVersion(geminiService.getPromptVersion())
                    .model(geminiService.getModelName())
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                AiAdvice savedAdvice = aiAdviceRepository.save(newAdvice);
                log.info("[AiAdviceService] AI 조언 저장 완료. User ID={}, Season ID={}, Advice ID={}", userId, seasonId, savedAdvice.getId());
                return convertToDto(savedAdvice);
            } catch (DataIntegrityViolationException e) {
                // 같은 사용자가 거의 동시에 두 번 요청해 다른 요청이 먼저 저장한 경우: 그쪽 결과를 돌려준다
                log.info("[AiAdviceService] 다른 요청이 먼저 저장한 AI 조언을 반환합니다. User ID={}, Season ID={}", userId, seasonId);
                return aiAdviceRepository.findByUserAndSeason(user, season)
                        .map(this::convertToDto)
                        .orElseThrow(() -> e);
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 런타임 오류 발생: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, e);
        }
    }

    /**
     * 특정 사용자 및 특정 시즌에 대한 AI 투자 조언을 조회합니다.
     * 이 메서드는 시즌 상태에 관계없이 조언이 있으면 조회합니다. (필요 시 시즌 상태 체크 추가 가능)
     *
     * @param userId 조회할 사용자의 ID
     * @param seasonId 조회할 시즌의 ID
     * @return 조회된 AiAdviceDto (존재할 경우) 또는 null
     */
    @Transactional(readOnly = true)
    public AiAdviceDto getAiAdviceForUserAndSeason(Integer userId, Integer seasonId) {
        log.info("[AiAdviceService] getAiAdviceForUserAndSeason 요청 시작: User ID={}, Season ID={}", userId, seasonId);
        try {
            // userId를 기반으로 User 엔티티 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            log.debug("[AiAdviceService] getAiAdviceForUserAndSeason - 사용자 엔티티 조회 완료. User ID={}", userId);

            Season season = seasonRepository.findById(seasonId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
            log.debug("[AiAdviceService] getAiAdviceForUserAndSeason - 시즌 엔티티 조회 완료. Season ID={}", seasonId);

            Optional<AiAdvice> adviceOptional = aiAdviceRepository.findByUserAndSeason(user, season);
            if (adviceOptional.isPresent()) {
                log.info("[AiAdviceService] getAiAdviceForUserAndSeason - AI 조언 조회 성공. User ID={}, Season ID={}, Advice ID={}",
                        userId, seasonId, adviceOptional.get().getId());
                return convertToDto(adviceOptional.get());
            } else {
                log.info("[AiAdviceService] getAiAdviceForUserAndSeason - 해당 사용자 및 시즌에 대한 AI 조언을 찾을 수 없습니다. User ID={}, Season ID={}", userId, seasonId);
                return null;
            }
        } catch (IllegalArgumentException e) {
            log.error("[AiAdviceService] getAiAdviceForUserAndSeason - 입력 값 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[AiAdviceService] getAiAdviceForUserAndSeason - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    private AiAdviceDto convertToDto(AiAdvice aiAdvice) {
        return AiAdviceDto.builder()
                .id(aiAdvice.getId())
                .userId(aiAdvice.getUser().getId())
                .seasonId(aiAdvice.getSeason().getId())
                .score(aiAdvice.getScore()) // AiAdvice 엔티티의 getScore는 Integer를 반환해야 합니다.
                .advice(aiAdvice.getAdvice())
                .promptVersion(aiAdvice.getPromptVersion())
                .createdAt(aiAdvice.getCreatedAt())
                .build();
    }
}
