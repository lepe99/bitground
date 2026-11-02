// bit/bitgroundspring/service/AssetSummaryService.java

package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.UserDailyBalance;
import bit.bitgroundspring.repository.UserDailyBalanceRepository;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetSummaryService {

    private final UserRepository userRepository;
    private final SeasonRepository seasonRepository;
    private final UserDailyBalanceRepository userDailyBalanceRepository;

    /**
     * 특정 사용자 및 시즌에 대한 자산 요약 데이터를 생성합니다.
     * 이 서비스는 Go 서비스가 생성한 UserDailyBalance 데이터를 '조회'하여 통계 및 추이를 제공합니다.
     * 데이터의 생성/수정은 Go 서비스가 담당합니다.
     *
     * @param userId 분석할 사용자의 ID
     * @param seasonId 분석할 시즌의 ID
     * @return InvestmentSummaryDto DTO (자산 분석 결과 요약)
     */
    public InvestmentSummaryDto getUserAssetSummaryForSeason(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found with ID: " + seasonId));

        LocalDate seasonStartDate = season.getStartAt();
        LocalDate seasonEndDate = season.getEndAt();

        // 메서드 이름을 findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc 로 수정
        List<UserDailyBalance> dailyBalances = userDailyBalanceRepository.findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                user, season, seasonStartDate, seasonEndDate);

        if (dailyBalances.isEmpty()) {
            log.warn("No daily balance data found for user {} in season {}. Returning default summary.", userId, seasonId);
            return InvestmentSummaryDto.builder()
                    .userId(user.getId())
                    .userName(user.getName())
                    .seasonId(season.getId())
                    .seasonName(season.getName())
                    .seasonStartAt(season.getStartAt())
                    .seasonEndAt(season.getEndAt())
                    .initialCashBalance(0) // Integer 기본값으로 수정
                    .finalTotalValue(0)    // Integer 기본값으로 수정
                    .totalProfitLossAmount(0) // Integer 기본값으로 수정
                    .totalProfitLossPercentage(0.0f)
                    .build();
        }

        // UserDailyBalance 엔티티의 totalValue가 Integer이므로 Integer로 받습니다.
        Integer initialTotalValue = dailyBalances.get(0).getTotalValue();
        Integer finalTotalValue = dailyBalances.get(dailyBalances.size() - 1).getTotalValue();

        // 총 손익 금액 계산 (Integer)
        Integer totalProfitLossAmount = finalTotalValue - initialTotalValue;

        // 총 수익률 계산 (Float으로 형변환하여 소수점 계산)
        Float totalProfitLossPercentage = 0.0f;
        if (initialTotalValue != 0) { // 0으로 나누는 것을 방지
            totalProfitLossPercentage = ((float) totalProfitLossAmount / initialTotalValue) * 100.0f;
        }

        return InvestmentSummaryDto.builder()
                .userId(user.getId())
                .userName(user.getName())
                .seasonId(season.getId())
                .seasonName(season.getName())
                .seasonStartAt(season.getStartAt())
                .seasonEndAt(season.getEndAt())
                .initialCashBalance(initialTotalValue) // Integer 값 전달
                .finalTotalValue(finalTotalValue)    // Integer 값 전달
                .totalProfitLossAmount(totalProfitLossAmount) // Integer 값 전달
                .totalProfitLossPercentage(totalProfitLossPercentage) // Float 값 전달
                .finalRank(null)
                .totalTradeCount(null)
                .buyOrderCount(null)
                .sellOrderCount(null)
                .avgTradeAmount(null)
                .avgTradesPerDay(null)
                .focusedOnFewCoins(null)
                .totalRealizedProfitLoss(null)
                .coinRealizedProfitLoss(null)
                .coinTradeCounts(null)
                .mostTradedCoinSymbol(null)
                .mostTradedCoinTradeVolume(null)
                .highestProfitCoinSymbol(null)
                .highestProfitCoinAmount(null)
                .lowestProfitCoinSymbol(null)
                .lowestProfitCoinAmount(null)
                .build();
    }

    /**
     * 특정 사용자 및 시즌에 대한 일별 총 자산 가치 변화 리스트를 조회합니다.
     * 차트 시각화 등에 사용될 수 있습니다.
     *
     * @param userId 조회할 사용자의 ID
     * @param seasonId 조회할 시즌의 ID
     * @return 일별 자산 가치 스냅샷 목록 (UserDailyBalance 엔티티)
     */
    public List<UserDailyBalance> getDailyBalanceTrend(Integer userId, Integer seasonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found with ID: " + seasonId));

        LocalDate seasonStartDate = season.getStartAt();
        LocalDate seasonEndDate = season.getEndAt();

        // 메서드 이름을 findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc 로 수정
        return userDailyBalanceRepository.findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                user, season, seasonStartDate, seasonEndDate);
    }
}
