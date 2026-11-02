// src/main/java/bit/bitgroundspring/service/AiAdviceService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.AiAdviceDto;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.AiAdvice;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.AiAdviceRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 특정 사용자 및 특정 시즌에 대한 AI 투자 조언을 생성하고 저장합니다.
     * 시즌 상태가 COMPLETED일 때만 작동하며, 이미 조언이 존재하는 경우 기존 조언을 반환합니다.
     *
     * @param userId 조언을 생성할 사용자의 ID
     * @param seasonId 조언을 생성할 시즌의 ID
     * @return 생성되거나 조회된 AiAdviceDto
     */
    @Transactional
    public AiAdviceDto generateOrGetAiAdviceForUserAndSeason(Integer userId, Integer seasonId) {
        log.info("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason 요청 시작: User ID={}, Season ID={}", userId, seasonId);
        try {
            // userId를 기반으로 User 엔티티 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            log.debug("[AiAdviceService] 사용자 엔티티 조회 완료. User ID={}", userId);

            // Season 엔티티 조회 및 상태 확인
            Season season = seasonRepository.findById(seasonId)
                    .orElseThrow(() -> new IllegalArgumentException("Season not found with ID: " + seasonId));
            log.debug("[AiAdviceService] 시즌 엔티티 조회 완료. Season ID={}, Status={}", seasonId, season.getStatus());

            // 시즌의 상태가 COMPLETED인지 확인
            if (season.getStatus() != Status.COMPLETED) {
                log.warn("[AiAdviceService] AI 조언은 COMPLETED 상태의 시즌에 대해서만 생성 또는 조회할 수 있습니다. 현재 시즌 상태: {}. User ID={}, Season ID={}",
                        season.getStatus(), userId, seasonId);
                throw new IllegalStateException("AI 조언은 'COMPLETED' 상태의 시즌에 대해서만 요청할 수 있습니다.");
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
                throw new RuntimeException("투자 요약 데이터를 생성할 수 없습니다.");
            }
            log.debug("[AiAdviceService] 투자 요약 데이터 생성 완료: {}", summaryDto);

            log.info("[AiAdviceService] GeminiService.generateInvestmentAdvice() 호출 중...");
            AiAdviceDto generatedAdviceDto = geminiService.generateInvestmentAdvice(summaryDto);
            if (generatedAdviceDto == null) {
                log.error("[AiAdviceService] Gemini API로부터 AI 조언 생성 실패. generateInvestmentAdvice()가 null을 반환했습니다. User ID={}, Season ID={}", userId, seasonId);
                throw new RuntimeException("AI 조언 생성에 실패했습니다 (Gemini API 호출 오류 또는 빈 응답).");
            }
            if (generatedAdviceDto.getAdvice() == null || generatedAdviceDto.getAdvice().isEmpty()) {
                log.warn("[AiAdviceService] Gemini API로부터 받은 조언 내용이 비어 있습니다. User ID={}, Season ID={}", userId, seasonId);
                throw new RuntimeException("AI 조언 내용이 비어 있습니다.");
            }
            log.info("[AiAdviceService] Gemini API로부터 AI 조언 성공적으로 생성. Score: {}, Advice Length: {}",
                    generatedAdviceDto.getScore(), generatedAdviceDto.getAdvice().length());

            // ⭐⭐⭐ 문제 해결 지점: generatedAdviceDto.getScore()의 런타임 타입과 값 확인 및 명시적 형변환 ⭐⭐⭐
            Object rawScore = generatedAdviceDto.getScore(); // 디버깅을 위해 Object로 받아서 실제 타입 확인
            log.debug("[AiAdviceService] generatedAdviceDto.getScore()의 런타임 타입: {}", rawScore != null ? rawScore.getClass().getName() : "null");
            log.debug("[AiAdviceService] generatedAdviceDto.getScore()의 값: {}", rawScore);

            Integer finalScore;
            if (rawScore instanceof Number) { // Number (Integer, Float, Double 등) 인스턴스인지 확인
                finalScore = ((Number) rawScore).intValue(); // Number를 intValue()로 변환 후 Integer로 오토박싱
            } else if (rawScore instanceof String) { // 문자열인 경우 파싱 시도
                try {
                    finalScore = Integer.parseInt((String) rawScore);
                } catch (NumberFormatException e) {
                    log.error("[AiAdviceService] Score 파싱 오류: 문자열 score '{}'를 Integer로 변환할 수 없습니다.", rawScore, e);
                    finalScore = 0; // 변환 실패 시 기본값 설정
                }
            } else {
                log.warn("[AiAdviceService] 예상치 못한 score 타입: {}", rawScore != null ? rawScore.getClass().getName() : "null");
                finalScore = 0; // 예상치 못한 타입일 경우 기본값 설정
            }

            AiAdvice newAdvice = AiAdvice.builder()
                    .user(user)
                    .season(season)
                    .score(finalScore) // ⭐⭐⭐ 수정된 finalScore 사용 ⭐⭐⭐
                    .advice(generatedAdviceDto.getAdvice())
                    .createdAt(LocalDateTime.now())
                    .build();
            log.debug("[AiAdviceService] AiAdvice 엔티티 빌드 완료. 저장 시도 중...");

            AiAdvice savedAdvice = aiAdviceRepository.save(newAdvice);
            log.info("[AiAdviceService] AI 조언이 성공적으로 데이터베이스에 저장되었습니다. User ID={}, Season ID={}. Advice ID: {}",
                    userId, seasonId, savedAdvice.getId());

            return convertToDto(savedAdvice);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 런타임 오류 발생: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("[AiAdviceService] generateOrGetAiAdviceForUserAndSeason - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("AI 조언 생성/저장 중 예상치 못한 오류가 발생했습니다.", e);
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
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            log.debug("[AiAdviceService] getAiAdviceForUserAndSeason - 사용자 엔티티 조회 완료. User ID={}", userId);

            Season season = seasonRepository.findById(seasonId)
                    .orElseThrow(() -> new IllegalArgumentException("Season not found with ID: " + seasonId));
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
                .createdAt(aiAdvice.getCreatedAt())
                .build();
    }
}
