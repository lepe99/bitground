package bit.bitgroundspring.ai;

import bit.bitgroundspring.ai.dto.AiAdviceDto;
import bit.bitgroundspring.ai.dto.InvestmentSummaryDto;
import bit.bitgroundspring.ai.dto.SeasonReviewContent;
import bit.bitgroundspring.common.Status;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.season.Season;
import bit.bitgroundspring.season.SeasonRepository;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiAdviceServiceTest {

    private final User user = User.builder().id(3).name("테스터").build();
    private final Season season = Season.builder().id(1).name("시즌 1").status(Status.COMPLETED).build();

    private GeminiService geminiService;
    private AiAdviceRepository aiAdviceRepository;
    private AiAdviceService service;

    @BeforeEach
    void setUp() {
        geminiService = mock(GeminiService.class);
        aiAdviceRepository = mock(AiAdviceRepository.class);
        InvestmentAnalysisService analysis = mock(InvestmentAnalysisService.class);
        UserRepository userRepository = mock(UserRepository.class);
        SeasonRepository seasonRepository = mock(SeasonRepository.class);
        service = new AiAdviceService(analysis, geminiService, aiAdviceRepository, userRepository, seasonRepository, new ObjectMapper());

        when(userRepository.findById(3)).thenReturn(Optional.of(user));
        when(seasonRepository.findById(1)).thenReturn(Optional.of(season));
        when(aiAdviceRepository.findByUserAndSeason(user, season)).thenReturn(Optional.empty());
        when(aiAdviceRepository.save(any())).thenAnswer(inv -> {
            AiAdvice a = inv.getArgument(0);
            a.setId(100);
            return a;
        });
        when(analysis.getUserInvestmentSummaryForSeason(3, 1)).thenReturn(InvestmentSummaryDto.builder()
                .seasonName("시즌 1").seasonStartAt(LocalDate.of(2026, 11, 2)).seasonEndAt(LocalDate.of(2026, 11, 4))
                .initialCashBalance(10_000_000L).finalTotalValue(11_000_000L)
                .totalProfitLossAmount(1_000_000L).totalProfitLossPercentage(10.0f)
                .finalRank(3).participantCount(24)
                .totalTradeCount(10).buyOrderCount(5).sellOrderCount(5).avgTradeAmount(1_000_000L)
                .coinRealizedProfitLoss(Map.of("KRW-BTC", 1_000_000L)).coinTradeCounts(Map.of("KRW-BTC", 10))
                .avgTradesPerDay(3.0).focusedOnFewCoins(true)
                .build());
    }

    private static SeasonReviewContent review() {
        return new SeasonReviewContent("총평", List.of("잘한 점"), List.of("아쉬운 점"), List.of(), "이유", null);
    }

    @Test
    void 생성_중에_같은_요청이_오면_모델을_다시_부르지_않고_결과를_함께_받는다() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(geminiService.generateSeasonReview(any(), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS); // 모델 응답을 기다리는 수십 초
            return review();
        });

        CompletableFuture<AiAdviceDto> first = CompletableFuture.supplyAsync(() -> service.generateOrGetAiAdviceForUserAndSeason(3, 1));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        // 다른 화면에 갔다 와서 다시 누른 요청
        CompletableFuture<AiAdviceDto> second = CompletableFuture.supplyAsync(() -> service.generateOrGetAiAdviceForUserAndSeason(3, 1));
        Thread.sleep(200);
        assertThat(second).isNotDone();

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).getId()).isEqualTo(100);
        assertThat(second.get(5, TimeUnit.SECONDS).getId()).isEqualTo(100);
        verify(geminiService, times(1)).generateSeasonReview(any(), any());
        verify(aiAdviceRepository, times(1)).save(any());
    }

    @Test
    void 진행_중이던_생성이_실패하면_기다리던_요청도_같은_오류를_받고_다음_요청은_새로_만든다() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(geminiService.generateSeasonReview(any(), any()))
                .thenAnswer(inv -> {
                    entered.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    throw new BusinessException(ErrorCode.AI_ADVICE_UNAVAILABLE);
                })
                .thenReturn(review());

        CompletableFuture<AiAdviceDto> first = CompletableFuture.supplyAsync(() -> service.generateOrGetAiAdviceForUserAndSeason(3, 1));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<AiAdviceDto> waiting = CompletableFuture.supplyAsync(() -> service.generateOrGetAiAdviceForUserAndSeason(3, 1));
        Thread.sleep(200);
        release.countDown();

        assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> waiting.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        // 실패한 생성은 목록에서 빠지므로 다시 누르면 새로 만든다
        assertThat(service.generateOrGetAiAdviceForUserAndSeason(3, 1).getId()).isEqualTo(100);
        verify(geminiService, times(2)).generateSeasonReview(any(), any());
    }
}
