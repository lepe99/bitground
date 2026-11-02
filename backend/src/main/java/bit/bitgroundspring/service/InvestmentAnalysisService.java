// src/main/java/bit/bitgroundspring/service/InvestmentAnalysisService.java
package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.RankRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserDailyBalanceRepository; // UserDailyBalanceRepository 임포트
import bit.bitgroundspring.repository.CoinPriceHistoryRepository; // CoinPriceHistoryRepository 임포트
import bit.bitgroundspring.repository.CoinRepository; // CoinRepository 임포트
import bit.bitgroundspring.util.InitialCashUtil;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate; // LocalDate 임포트
import java.time.temporal.ChronoUnit;
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
    private final RankRepository userRankingRepository;
    private final UserRepository userRepository;
    private final SeasonRepository seasonRepository;
    private final InitialCashUtil initialCashUtil;
    private final UserDailyBalanceRepository userDailyBalanceRepository;
    private final CoinPriceHistoryRepository coinPriceHistoryRepository;
    private final CoinRepository coinRepository;

    /**
     * 특정 사용자 및 시즌에 대한 투자 요약 데이터를 생성합니다.
     * 이 데이터는 AI 조언 생성을 위한 입력으로 사용됩니다.
     *
     * @param userId 분석할 사용자의 ID
     * @param seasonId 분석할 시즌의 ID
     * @return InvestmentSummaryDto DTO (분석 결과 요약)
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 시즌을 찾을 수 없는 경우
     */
    public InvestmentSummaryDto getUserInvestmentSummaryForSeason(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found with ID: " + seasonId));

        log.info("사용자 ID: {}, 시즌 ID: {}에 대한 투자 요약 계산 시작.", user.getId(), season.getId());

        // 1. 시즌 전체 성과 지표 로딩 및 계산
        // userRankingOptional은 순위 정보만 사용하고 totalValue는 아래에서 재계산합니다.
        Optional<UserRanking> userRankingOptional = userRankingRepository.findByUserAndSeason(user, season);
        UserRanking userRanking = userRankingOptional.orElse(null);

        Integer initialCashBalance = initialCashUtil.getInitialCash();

        // 2. 거래 활동 요약 (Order 엔티티 기반)
        // season.getStatus() == Status.COMPLETED 조건 확인
        List<Order> completedOrders = orderRepository.findByUserAndSeasonAndStatus(
                user, season, Status.COMPLETED);

        Integer totalTradeCount = completedOrders.size();
        Integer buyOrderCount = (int) completedOrders.stream()
                .filter(order -> order.getOrderType() == OrderType.BUY)
                .count();
        Integer sellOrderCount = (int) completedOrders.stream()
                .filter(order -> order.getOrderType() == OrderType.SELL)
                .count();

        Float totalTradeValueSum = completedOrders.stream()
                .map(order -> (float)(order.getTradePrice() * order.getAmount()))
                .reduce(0.0F, Float::sum);
        Float avgTradeAmount = (totalTradeCount > 0)
                ? totalTradeValueSum / totalTradeCount
                : 0.0F;

        Float totalRealizedProfitLoss = 0.0F;
        Map<String, Float> coinRealizedProfitLoss = new HashMap<>();

        Map<String, List<Order>> ordersByCoin = completedOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getCoin().getSymbol()));

        for (Map.Entry<String, List<Order>> entry : ordersByCoin.entrySet()) {
            String coinSymbol = entry.getKey();
            List<Order> coinOrders = entry.getValue();

            Float coinBuyValue = 0.0F;
            Float coinSellValue = 0.0F;

            for (Order order : coinOrders) {
                Float tradeValue = (float)(order.getTradePrice() * order.getAmount());
                if (order.getOrderType() == OrderType.BUY) {
                    coinBuyValue += tradeValue;
                } else if (order.getOrderType() == OrderType.SELL) {
                    coinSellValue += tradeValue;
                }
            }
            Float currentCoinPL = coinSellValue - coinBuyValue;
            coinRealizedProfitLoss.put(coinSymbol, currentCoinPL);
            totalRealizedProfitLoss += currentCoinPL;
        }

        Map<String, Integer> coinTradeCounts = completedOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getCoin().getSymbol(),
                        Collectors.summingInt(order -> 1)));

        // 3. 투자 성향 관련 지표 (Order 엔티티 기반 심화 분석)
        String mostTradedCoinSymbol = null;
        Float mostTradedCoinTradeVolume = 0.0F;
        if (!completedOrders.isEmpty()) {
            Map<String, Float> coinVolumes = new HashMap<>();
            for (Order order : completedOrders) {
                String symbol = order.getCoin().getSymbol();
                Float tradeVolume = (float)(order.getTradePrice() * order.getAmount());
                coinVolumes.put(symbol, coinVolumes.getOrDefault(symbol, 0.0F) + tradeVolume);
            }

            mostTradedCoinSymbol = coinVolumes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            mostTradedCoinTradeVolume = coinVolumes.getOrDefault(mostTradedCoinSymbol, 0.0F);
        }

        String highestProfitCoinSymbol = null;
        Float highestProfitCoinAmount = 0.0F;
        String lowestProfitCoinSymbol = null;
        Float lowestProfitCoinAmount = 0.0F;

        if (!coinRealizedProfitLoss.isEmpty()) {
            highestProfitCoinSymbol = coinRealizedProfitLoss.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            highestProfitCoinAmount = coinRealizedProfitLoss.getOrDefault(highestProfitCoinSymbol, 0.0F);

            lowestProfitCoinSymbol = coinRealizedProfitLoss.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            lowestProfitCoinAmount = coinRealizedProfitLoss.getOrDefault(lowestProfitCoinSymbol, 0.0F);
        }

        long seasonDurationDays = ChronoUnit.DAYS.between(season.getStartAt(), season.getEndAt().plusDays(1));
        Double avgTradesPerDay = (seasonDurationDays > 0)
                ? (double) totalTradeCount / seasonDurationDays
                : (totalTradeCount > 0 ? (double)totalTradeCount : 0.0);

        Boolean focusedOnFewCoins = false;
        if (totalTradeCount > 0) {
            long distinctCoinsTraded = completedOrders.stream()
                    .map(order -> order.getCoin().getSymbol())
                    .distinct()
                    .count();
            // 전체 거래 중 3개 이하의 코인에 집중했을 경우 true
            if (distinctCoinsTraded <= 3 && totalTradeCount > 0) {
                focusedOnFewCoins = true;
            }
        }

        // 4. 일별 자산 잔고 추이 데이터 조회 및 추가
        List<UserDailyBalance> dailyBalanceTrend = userDailyBalanceRepository
                .findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        user, season, season.getStartAt(), season.getEndAt()
                );

        // 5. 주요 코인들의 시즌 내 시세 변동 요약
        Map<String, String> coinMarketPerformanceSummary = new HashMap<>();
        List<String> keyCoinSymbols = new java.util.ArrayList<>();
        if (mostTradedCoinSymbol != null) keyCoinSymbols.add(mostTradedCoinSymbol);
        if (highestProfitCoinSymbol != null && !keyCoinSymbols.contains(highestProfitCoinSymbol)) keyCoinSymbols.add(highestProfitCoinSymbol);
        if (lowestProfitCoinSymbol != null && !keyCoinSymbols.contains(lowestProfitCoinSymbol)) keyCoinSymbols.add(lowestProfitCoinSymbol);

        for (String symbol : keyCoinSymbols) {
            Optional<Coin> coinOptional = coinRepository.findBySymbol(symbol);
            if (coinOptional.isPresent()) {
                Coin coin = coinOptional.get();

                // 시즌 시작일의 시가: 해당 날짜의 가장 이른 시간 데이터의 openPrice
                List<CoinPriceHistory> startDayPrices = coinPriceHistoryRepository.findByCoinAndDateOrderByHourAsc(coin, season.getStartAt());
                Float startPrice = startDayPrices.isEmpty() ? null : startDayPrices.get(0).getOpenPrice();

                // 시즌 종료일의 종가: 해당 날짜의 가장 늦은 시간 데이터의 closePrice
                List<CoinPriceHistory> endDayPrices = coinPriceHistoryRepository.findByCoinAndDateOrderByHourDesc(coin, season.getEndAt());
                Float endPrice = endDayPrices.isEmpty() ? null : endDayPrices.get(0).getClosePrice();

                if (startPrice != null && endPrice != null && startPrice != 0) {
                    String performanceSummary = String.format(
                            "초기 %.2f원 -> 최종 %.2f원 (변동률: %.2f%%)",
                            startPrice,
                            endPrice,
                            ((endPrice - startPrice) / startPrice) * 100
                    );
                    coinMarketPerformanceSummary.put(symbol, performanceSummary);
                } else {
                    log.warn("코인 {}의 시즌 시작/종료 가격 데이터를 찾을 수 없거나 시작 가격이 0입니다. (시즌 기간: {} ~ {})",
                            symbol, season.getStartAt(), season.getEndAt());
                }
            } else {
                log.warn("코인 심볼 {}에 해당하는 Coin 엔티티를 찾을 수 없습니다.", symbol);
            }
        }

        // ⭐⭐ 수정된 부분: finalTotalValue와 totalProfitLossAmount/Percentage 재계산 ⭐⭐
        // 시즌이 COMPLETED 상태라면, 모든 자산이 매도 처리되어 totalRealizedProfitLoss에 반영되었다고 가정합니다.
        // 따라서 최종 총 자산은 초기 자산 + 총 실현 손익으로 계산합니다.
        Integer finalTotalValue;
        Integer totalProfitLossAmountRecalculated; // 기존 totalProfitLossAmount와 구분
        Float totalProfitLossPercentageRecalculated; // 기존 totalProfitLossPercentage와 구분

        if (season.getStatus() == Status.COMPLETED) {
            // 시즌이 완료된 경우, 최종 총 자산은 초기 현금에 총 실현 손익을 더한 값으로 간주
            finalTotalValue = initialCashBalance + Math.round(totalRealizedProfitLoss);
            totalProfitLossAmountRecalculated = finalTotalValue - initialCashBalance;
            totalProfitLossPercentageRecalculated = (initialCashBalance != 0) ?
                    ((float) totalProfitLossAmountRecalculated / initialCashBalance) * 100.0F : 0.0F;
            log.info("COMPLETED 시즌의 finalTotalValue 재계산: 초기자금 {} + 실현손익 {} = 최종자산 {}",
                    initialCashBalance, totalRealizedProfitLoss, finalTotalValue);
        } else {
            // 진행 중인 시즌의 경우, user_rankings의 totalValue를 사용 (혹은 실시간 자산 평가 로직)
            // 현재는 user_rankings의 값을 그대로 사용하거나, 필요시 실시간 평가 로직 추가
            finalTotalValue = (userRanking != null && userRanking.getTotalValue() != null)
                    ? userRanking.getTotalValue()
                    : initialCashBalance; // 랭크 데이터 없으면 초기 현금으로 시작 (임시)
            totalProfitLossAmountRecalculated = finalTotalValue - initialCashBalance;
            totalProfitLossPercentageRecalculated = (initialCashBalance != 0) ?
                    ((float) totalProfitLossAmountRecalculated / initialCashBalance) * 100.0F : 0.0F;
            log.info("ACTIVE 시즌의 finalTotalValue 사용: UserRanking의 최종자산 {} (초기자금 {})",
                    finalTotalValue, initialCashBalance);
        }


        // 6. 모든 정보를 포함하는 InvestmentSummaryDto 빌드
        return InvestmentSummaryDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .seasonId(season.getId())
                .seasonName(season.getName())
                .seasonStartAt(season.getStartAt())
                .seasonEndAt(season.getEndAt())
                .initialCashBalance(initialCashBalance)
                .finalTotalValue(finalTotalValue) // ✅ 재계산된 값 사용
                .totalProfitLossAmount(totalProfitLossAmountRecalculated) // ✅ 재계산된 값 사용
                .totalProfitLossPercentage(totalProfitLossPercentageRecalculated) // ✅ 재계산된 값 사용
                .finalRank((userRanking != null && userRanking.getRanks() != null) ? userRanking.getRanks() : null)
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
}
