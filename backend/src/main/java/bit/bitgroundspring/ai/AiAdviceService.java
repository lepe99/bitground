
package bit.bitgroundspring.ai;

import bit.bitgroundspring.ai.dto.AiAdviceDto;
import bit.bitgroundspring.ai.dto.InvestmentSummaryDto;
import bit.bitgroundspring.ai.dto.SeasonReviewContent;
import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.season.SeasonRepository;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    // 사용자·시즌별로 진행 중인 리뷰 생성 ("userId:seasonId" → 결과)
    private final ConcurrentMap<String, CompletableFuture<AiAdviceDto>> inFlight = new ConcurrentHashMap<>();

    /**
     * 특정 사용자 및 특정 시즌에 대한 AI 투자 조언을 생성하고 저장합니다.
     * 시즌 상태가 COMPLETED일 때만 작동하며, 이미 조언이 존재하는 경우 기존 조언을 반환합니다.
     *
     * <p>트랜잭션을 걸지 않는다. Gemini 응답을 기다리는 수십 초 동안 DB 연결을 붙잡지 않도록
     * 조회(투자 요약은 자체 읽기 전용 트랜잭션)와 저장만 각각 짧게 DB를 쓴다.
     *
     * <p>같은 사용자·시즌의 생성이 이미 진행 중이면 모델을 다시 부르지 않고 그 결과를 함께 기다린다.
     * (생성 중에 다른 화면에 갔다 오거나 새로고침해서 버튼을 다시 누르면 Gemini를 한 번 더 호출하던 문제.
     * 서버가 여러 대면 대마다 따로지만, 그때도 저장은 유니크 키로 하나만 남는다)
     *
     * @param userId 조언을 생성할 사용자의 ID
     * @param seasonId 조언을 생성할 시즌의 ID
     * @return 생성되거나 조회된 AiAdviceDto
     */
    @Loggable(value = "AI 조언", slowMs = 30_000) // 모델 응답은 보통 2~20초
    public AiAdviceDto generateOrGetAiAdviceForUserAndSeason(Integer userId, Integer seasonId) {
        String key = userId + ":" + seasonId;
        CompletableFuture<AiAdviceDto> mine = new CompletableFuture<>();
        CompletableFuture<AiAdviceDto> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            log.info("AI 리뷰: 같은 요청이 진행 중이라 그 결과를 기다림 (user={}, season={})", userId, seasonId);
            try {
                return running.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException cause ? cause : e;
            }
        }
        try {
            AiAdviceDto result = generate(userId, seasonId);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private AiAdviceDto generate(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
        // 끝난 시즌만 돌아본다
        if (season.getStatus() != Status.COMPLETED) {
            throw new BusinessException(ErrorCode.SEASON_NOT_COMPLETED);
        }

        // 이미 만들었으면 다시 만들지 않는다 (시즌마다 한 번)
        Optional<AiAdvice> existing = aiAdviceRepository.findByUserAndSeason(user, season);
        if (existing.isPresent()) {
            return convertToDto(existing.get());
        }

        InvestmentSummaryDto summaryDto = investmentAnalysisService.getUserInvestmentSummaryForSeason(userId, seasonId);
        if (summaryDto == null) {
            log.warn("AI 리뷰: 투자 요약을 만들지 못함 (user={}, season={})", userId, seasonId);
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, "투자 요약 데이터를 생성할 수 없습니다.");
        }
        // 랭킹에 없으면(그 시즌에 체결한 거래가 없으면) 돌아볼 기록이 없으므로 모델을 부르지 않는다
        if (summaryDto.getFinalRank() == null || summaryDto.getTotalTradeCount() == 0) {
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, "이 시즌에는 거래 기록이 없어 리뷰를 만들 수 없어요.");
        }

        // 점수는 정해진 규칙으로 코드가 계산하고, 모델은 그 이유와 회고만 쓴다
        SeasonReviewScorer.Score score = SeasonReviewScorer.score(summaryDto);
        SeasonReviewContent review;
        try {
            review = geminiService.generateSeasonReview(summaryDto, score).withScoreBreakdown(score.parts());
        } catch (IOException e) { // 모델 호출 실패·타임아웃
            log.warn("AI 리뷰: 모델 호출 실패 (user={}, season={}): {}", userId, seasonId, e.toString());
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, e);
        }

        AiAdvice newAdvice;
        try {
            newAdvice = AiAdvice.builder()
                    .user(user)
                    .season(season)
                    .score(score.total())
                    .advice(objectMapper.writeValueAsString(review))
                    .promptVersion(geminiService.getPromptVersion())
                    .model(geminiService.getModelName())
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE, e);
        }

        try {
            AiAdvice saved = aiAdviceRepository.save(newAdvice);
            log.info("AI 리뷰 생성 (user={}, season={}, score={}, model={})", userId, seasonId, score.total(), saved.getModel());
            return convertToDto(saved);
        } catch (DataIntegrityViolationException e) {
            // 다른 서버에서 같은 사용자가 거의 동시에 요청해 그쪽이 먼저 저장한 경우: 그 결과를 돌려준다
            log.info("AI 리뷰: 먼저 저장된 결과를 돌려줌 (user={}, season={})", userId, seasonId);
            return aiAdviceRepository.findByUserAndSeason(user, season)
                    .map(this::convertToDto)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * 이미 만든 AI 리뷰 조회 (없으면 null). 새로 만들지는 않는다 (생성은 사용자가 버튼을 눌렀을 때만)
     * 기존: 모든 예외를 잡아 null을 돌려줘, 없는 시즌·사용자도 "리뷰 없음"으로 보였고 매 조회마다 INFO 로그가 남았다
     */
    @Transactional(readOnly = true)
    public AiAdviceDto getAiAdviceForUserAndSeason(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
        return aiAdviceRepository.findByUserAndSeason(user, season)
                .map(this::convertToDto)
                .orElse(null);
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
