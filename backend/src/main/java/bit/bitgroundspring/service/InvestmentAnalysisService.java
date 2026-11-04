// src/main/java/bit/bitgroundspring/service/InvestmentAnalysisService.java
package bit.bitgroundspring.service;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserRankingRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserDailyBalanceRepository;
import bit.bitgroundspring.repository.CoinPriceHistoryRepository;
import bit.bitgroundspring.repository.CoinRepository;
import bit.bitgroundspring.repository.MarketIndexRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentAnalysisService {

    private final OrderRepository orderRepository;
    private final UserRankingRepository userRankingRepository;
    private final UserRepository userRepository;
    private final SeasonRepository seasonRepository;
    private final UserDailyBalanceRepository userDailyBalanceRepository;
    private final CoinPriceHistoryRepository coinPriceHistoryRepository;
    private final CoinRepository coinRepository;
    private final MarketIndexRepository marketIndexRepository;

    // 시장 지수 기록이 없을 때 쓰는 비교 기준 코인
    private static final String BENCHMARK_COIN = "KRW-BTC";

    /**
     * 특정 사용자 및 시즌에 대한 투자 요약 데이터를 생성합니다. (AI 시즌 리뷰의 입력)
     *
     * <p>총 손익·기준 자금 계산 방식 (완료된 시즌):
     * 시즌 정산 때 스케줄러가 남은 보유 코인을 랭킹과 같은 가격으로 청산해 SELL 주문으로 기록하므로,
     * 시즌 체결 내역의 (매도 금액 − 매수 금액)이 곧 시즌 전체 매매 손익이고,
     * 랭킹의 최종 총자산 − 매매 손익 = 시즌 동안 지급받은 현금(시즌 시작 지급 또는 시즌 중 가입 지급 + 스플릿 지급)이다.
     * 지급 규칙(가입일·스플릿 시각)을 다시 계산하지 않아도 되고, 화면의 순위·총자산과 숫자가 어긋나지 않는다.
     * (기존: 오늘 날짜 기준 초기 자금 정책 값을 기준으로 써서, 여는 날·가입 시점·스플릿에 따라 수익률이 틀렸음)
     *
     * <p>진행 중인 시즌은 아직 청산 전이라 보유 코인이 손익에 반영되지 않으므로 기준 자금·총 손익·수익률을 null로 둔다.
     */
    @Transactional(readOnly = true)
    public InvestmentSummaryDto getUserInvestmentSummaryForSeason(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));
        boolean completed = season.getStatus() == Status.COMPLETED;

        log.info("사용자 ID: {}, 시즌 ID: {}에 대한 투자 요약 계산 시작.", user.getId(), season.getId());

        UserRanking userRanking = userRankingRepository.findByUserAndSeason(user, season).orElse(null);
        int participantCount = (int) userRankingRepository.countBySeason(season);

        // 1. 거래 활동 요약 (금액은 BigDecimal로 합산 후 원 단위로 반올림. Float은 1,600만 원을 넘으면 원 단위가 틀어짐)
        List<Order> completedOrders = orderRepository.findByUserAndSeasonAndStatus(user, season, Status.COMPLETED);

        int totalTradeCount = completedOrders.size();
        int buyOrderCount = (int) completedOrders.stream().filter(o -> o.getOrderType() == OrderType.BUY).count();
        int sellOrderCount = totalTradeCount - buyOrderCount;

        Map<String, BigDecimal> coinProfit = new HashMap<>();   // 코인별 (매도 − 매수)
        Map<String, BigDecimal> coinVolume = new HashMap<>();   // 코인별 거래 금액 합
        Map<String, Integer> coinTradeCounts = new HashMap<>();
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (Order order : completedOrders) {
            String symbol = order.getCoin().getSymbol();
            BigDecimal value = order.getTradePrice().multiply(order.getAmount());
            BigDecimal signed = order.getOrderType() == OrderType.SELL ? value : value.negate();
            coinProfit.merge(symbol, signed, BigDecimal::add);
            coinVolume.merge(symbol, value, BigDecimal::add);
            coinTradeCounts.merge(symbol, 1, Integer::sum);
            totalVolume = totalVolume.add(value);
        }

        Map<String, Long> coinRealizedProfitLoss = coinProfit.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toWon(e.getValue())));
        long totalRealizedProfitLoss = toWon(coinProfit.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        long avgTradeAmount = totalTradeCount > 0
                ? toWon(totalVolume.divide(BigDecimal.valueOf(totalTradeCount), 0, RoundingMode.HALF_UP))
                : 0L;

        // 2. 투자 성향 지표
        String mostTradedCoinSymbol = maxKey(coinVolume);
        long mostTradedCoinTradeVolume = mostTradedCoinSymbol != null ? toWon(coinVolume.get(mostTradedCoinSymbol)) : 0L;
        String highestProfitCoinSymbol = maxKey(coinProfit);
        long highestProfitCoinAmount = highestProfitCoinSymbol != null ? coinRealizedProfitLoss.get(highestProfitCoinSymbol) : 0L;
        String lowestProfitCoinSymbol = coinProfit.entrySet().stream()
                .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        long lowestProfitCoinAmount = lowestProfitCoinSymbol != null ? coinRealizedProfitLoss.get(lowestProfitCoinSymbol) : 0L;

        long seasonDurationDays = ChronoUnit.DAYS.between(season.getStartAt(), season.getEndAt().plusDays(1));
        double avgTradesPerDay = seasonDurationDays > 0 ? (double) totalTradeCount / seasonDurationDays : totalTradeCount;

        // 거래한 코인이 3종 이하면 소수 코인 집중
        boolean focusedOnFewCoins = totalTradeCount > 0 && coinTradeCounts.size() <= 3;

        // 3. 일별 자산 잔고 추이
        List<UserDailyBalance> dailyBalanceTrend = userDailyBalanceRepository
                .findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        user, season, season.getStartAt(), season.getEndAt()
                );

        // 4. 주요 코인들의 시즌 내 시세 변동 요약 + 비교 기준(BTC) 등락
        List<String> keyCoins = distinct(mostTradedCoinSymbol, highestProfitCoinSymbol, lowestProfitCoinSymbol);
        Map<String, float[]> seasonPrices = seasonStartEndPrices(season, distinct(BENCHMARK_COIN, mostTradedCoinSymbol, highestProfitCoinSymbol, lowestProfitCoinSymbol));
        Map<String, String> coinMarketPerformanceSummary = new HashMap<>();
        for (String symbol : keyCoins) {
            float[] p = seasonPrices.get(symbol);
            if (p != null) {
                coinMarketPerformanceSummary.put(symbol, String.format(
                        "초기 %.2f원 -> 최종 %.2f원 (변동률: %.2f%%)", p[0], p[1], changePercent(p[0], p[1])));
            }
        }
        float[] btc = seasonPrices.get(BENCHMARK_COIN);
        Float btcChange = btc != null ? changePercent(btc[0], btc[1]) : null;
        Float marketIndexChange = marketIndexChange(season);
        Float maxDrawdown = maxDrawdownPercent(dailyBalanceTrend);

        // 5. 최종 자산·기준 자금·총 손익 (위 설명 참고)
        Long finalTotalValue = userRanking != null ? userRanking.getTotalValue() : null;
        Long grantedCash = null;
        Long totalProfitLoss = null;
        Float totalProfitLossPercentage = null;
        if (completed && finalTotalValue != null) {
            totalProfitLoss = totalRealizedProfitLoss;
            grantedCash = finalTotalValue - totalProfitLoss;
            totalProfitLossPercentage = grantedCash > 0 ? (float) (totalProfitLoss * 100.0 / grantedCash) : null;
        }

        return InvestmentSummaryDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .seasonId(season.getId())
                .seasonName(season.getName())
                .seasonStartAt(season.getStartAt())
                .seasonEndAt(season.getEndAt())
                .initialCashBalance(grantedCash)
                .finalTotalValue(finalTotalValue)
                .totalProfitLossAmount(totalProfitLoss)
                .totalProfitLossPercentage(totalProfitLossPercentage)
                .finalRank(userRanking != null ? userRanking.getRanks() : null)
                .participantCount(participantCount)
                .marketIndexChangePercentage(marketIndexChange)
                .btcChangePercentage(btcChange)
                .maxDrawdownPercentage(maxDrawdown)
                .totalTradeCount(totalTradeCount)
                .buyOrderCount(buyOrderCount)
                .sellOrderCount(sellOrderCount)
                .avgTradeAmount(avgTradeAmount)
                .totalRealizedProfitLoss(totalRealizedProfitLoss)
                .coinRealizedProfitLoss(coinRealizedProfitLoss)
                .coinTradeCounts(coinTradeCounts)
                .mostTradedCoinSymbol(mostTradedCoinSymbol)
                .mostTradedCoinTradeVolume(mostTradedCoinTradeVolume)
                .highestProfitCoinSymbol(highestProfitCoinSymbol)
                .highestProfitCoinAmount(highestProfitCoinAmount)
                .lowestProfitCoinSymbol(lowestProfitCoinSymbol)
                .lowestProfitCoinAmount(lowestProfitCoinAmount)
                .avgTradesPerDay(avgTradesPerDay)
                .focusedOnFewCoins(focusedOnFewCoins)
                .dailyBalanceTrend(dailyBalanceTrend)
                .coinMarketPerformanceSummary(coinMarketPerformanceSummary)
                .build();
    }

    /** 시장 지수의 시즌 첫 기록 → 마지막 기록 등락률. 기록이 없으면 null */
    private Float marketIndexChange(Season season) {
        Optional<MarketIndex> first = marketIndexRepository.findFirstByDateBetweenOrderByDateAscHourAsc(season.getStartAt(), season.getEndAt());
        Optional<MarketIndex> last = marketIndexRepository.findFirstByDateBetweenOrderByDateDescHourDesc(season.getStartAt(), season.getEndAt());
        if (first.isEmpty() || last.isEmpty() || first.get().getId().equals(last.get().getId())) return null;
        return changePercent(first.get().getMarketIndex(), last.get().getMarketIndex());
    }

    /**
     * 일별 총자산의 최대 낙폭(%, 양수). 직전 최고점 대비 가장 크게 떨어진 비율. 기록이 2일 미만이면 null.
     * 스플릿 지급은 자산을 늘리기만 하므로 낙폭을 부풀리지 않는다
     */
    static Float maxDrawdownPercent(List<UserDailyBalance> balances) {
        if (balances == null || balances.size() < 2) return null;
        long peak = 0;
        double worst = 0;
        for (UserDailyBalance b : balances) {
            long value = b.getTotalValue() != null ? b.getTotalValue() : 0;
            peak = Math.max(peak, value);
            if (peak > 0) worst = Math.max(worst, (peak - value) * 100.0 / peak);
        }
        return (float) worst;
    }

    private static float changePercent(float start, float end) {
        return start == 0 ? 0f : (end - start) / start * 100;
    }

    /**
     * 코인별 시즌 시작일 시가와 종료일 종가 {시가, 종가}. 쿼리 두 번으로 가져오고, 둘 중 하나라도 없으면 그 코인은 빠진다
     */
    private Map<String, float[]> seasonStartEndPrices(Season season, List<String> symbols) {
        Map<String, float[]> result = new HashMap<>();
        if (symbols.isEmpty()) return result;

        Map<String, Coin> coinsBySymbol = coinRepository.findAllBySymbolIn(symbols).stream()
                .collect(Collectors.toMap(Coin::getSymbol, c -> c));
        Map<String, List<CoinPriceHistory>> pricesBySymbol = coinsBySymbol.isEmpty() ? Map.of()
                : coinPriceHistoryRepository.findByCoinsAndDates(coinsBySymbol.values(), List.of(season.getStartAt(), season.getEndAt()))
                .stream()
                .collect(Collectors.groupingBy(h -> h.getCoin().getSymbol()));

        for (String symbol : symbols) {
            if (!coinsBySymbol.containsKey(symbol)) {
                log.warn("코인 심볼 {}에 해당하는 Coin 엔티티를 찾을 수 없습니다.", symbol);
                continue;
            }
            List<CoinPriceHistory> prices = pricesBySymbol.getOrDefault(symbol, List.of());

            // 시즌 시작일의 시가: 그날 가장 이른 시간의 openPrice / 종료일의 종가: 그날 가장 늦은 시간의 closePrice
            Float startPrice = prices.stream()
                    .filter(h -> h.getDate().equals(season.getStartAt()))
                    .min(Comparator.comparing(CoinPriceHistory::getHour))
                    .map(CoinPriceHistory::getOpenPrice)
                    .orElse(null);
            Float endPrice = prices.stream()
                    .filter(h -> h.getDate().equals(season.getEndAt()))
                    .max(Comparator.comparing(CoinPriceHistory::getHour))
                    .map(CoinPriceHistory::getClosePrice)
                    .orElse(null);

            if (startPrice != null && endPrice != null && startPrice != 0) {
                result.put(symbol, new float[]{startPrice, endPrice});
            } else {
                log.warn("코인 {}의 시즌 시작/종료 가격 데이터를 찾을 수 없거나 시작 가격이 0입니다. (시즌 기간: {} ~ {})",
                        symbol, season.getStartAt(), season.getEndAt());
            }
        }
        return result;
    }

    private static long toWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String maxKey(Map<String, BigDecimal> values) {
        return values.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static List<String> distinct(String... symbols) {
        List<String> result = new ArrayList<>();
        for (String s : symbols) {
            if (s != null && !result.contains(s)) result.add(s);
        }
        return result;
    }
}
