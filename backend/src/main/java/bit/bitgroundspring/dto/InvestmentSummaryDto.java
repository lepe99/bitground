// bit/bitgroundspring/dto/InvestmentSummaryDto.java

package bit.bitgroundspring.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List; // List 임포트 추가
import java.util.Map;

// UserDailyBalance 엔티티 임포트 (InvestmentSummaryDto에서 직접 참조하기 위해)
import bit.bitgroundspring.entity.UserDailyBalance;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentSummaryDto {

    // 1. 사용자 및 시즌 기본 정보
    private Integer userId;
    private String userName;
    private Integer seasonId;
    private String seasonName;
    private LocalDate seasonStartAt;
    private LocalDate seasonEndAt;

    // 2. 시즌 전체 성과 지표
    private Integer initialCashBalance;       // 시즌 시작 시 초기 현금 잔고
    private Integer finalTotalValue;          // 시즌 종료 시점 최종 총 자산
    private Integer totalProfitLossAmount;    // 총 손익 금액
    private Float totalProfitLossPercentage; // 총 수익률 (%)
    private Integer finalRank;             // 시즌 최종 랭크

    // 3. 거래 활동 요약
    private Integer totalTradeCount;             // 총 거래 횟수
    private Integer buyOrderCount;               // 총 매수 주문 횟수
    private Integer sellOrderCount;              // 총 매도 주문 횟수
    private Float avgTradeAmount;           // 평균 거래 금액
    private Float totalRealizedProfitLoss;  // 총 실현 손익
    private Map<String, Float> coinRealizedProfitLoss; // 코인별 실현 손익
    private Map<String, Integer> coinTradeCounts; // 코인별 거래 횟수

    // 4. 투자 성향 관련 지표
    private String mostTradedCoinSymbol;       // 가장 많이 거래한 코인 심볼
    private Float mostTradedCoinTradeVolume; // 가장 많이 거래한 코인의 총 거래 금액
    private String highestProfitCoinSymbol;    // 가장 수익을 많이 낸 코인 심볼
    private Float highestProfitCoinAmount;  // 가장 수익을 많이 낸 코인의 수익 금액
    private String lowestProfitCoinSymbol;     // 가장 손실을 많이 낸 코인 심볼
    private Float lowestProfitCoinAmount;   // 가장 손실을 많이 낸 코인의 손실 금액
    private Double avgTradesPerDay;            // 하루 평균 거래 횟수
    private Boolean focusedOnFewCoins;         // 소수 코인에 집중했는지 여부

    // AI 조언 DTO (이미 존재했음)
    private AiAdviceDto aiAdvice;

    // ✅ 추가: 일별 자산 잔고 추이 데이터
    // 이 데이터를 AI가 분석하여 시간 흐름에 따른 투자 조언을 생성할 수 있습니다.
    private List<UserDailyBalance> dailyBalanceTrend;
    // ✅ 추가: 주요 코인들의 시즌 내 시세 변동 요약
    private Map<String, String> coinMarketPerformanceSummary;
}
