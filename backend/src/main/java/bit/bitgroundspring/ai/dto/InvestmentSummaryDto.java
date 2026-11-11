
package bit.bitgroundspring.ai.dto;

import bit.bitgroundspring.season.UserDailyBalance;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List; // List 임포트 추가
import java.util.Map;

// UserDailyBalance 엔티티 임포트 (InvestmentSummaryDto에서 직접 참조하기 위해)

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

    // 2. 시즌 전체 성과 지표 (금액은 모두 원 단위 long)
    // AssetSummaryService: 시즌 첫날 스냅샷 기준 / InvestmentAnalysisService: 시즌 중 지급받은 현금 합계
    // (시즌 시작 지급 또는 시즌 중 가입 시 지급 + 스플릿 추가 지급). 진행 중인 시즌이면 null
    private Long initialCashBalance;
    private Long finalTotalValue;             // 시즌 종료 시점 최종 총 자산 (랭킹 정산 값)
    private Long totalProfitLossAmount;       // 총 손익 금액 (진행 중인 시즌이면 null)
    private Float totalProfitLossPercentage; // 총 수익률 (%) (진행 중인 시즌이면 null)
    private Integer finalRank;             // 시즌 최종 랭크
    private Integer participantCount;      // 시즌 랭킹 참가자 수
    private Float marketIndexChangePercentage; // 같은 기간 시장 지수 등락률 (%) — 비교 기준. 기록이 없으면 null
    private Float btcChangePercentage;         // 같은 기간 BTC 등락률 (%) — 시장 지수가 없을 때 비교 기준
    private Float maxDrawdownPercentage;       // 일별 총자산의 최대 낙폭 (%, 양수). 기록이 2일 미만이면 null

    // 3. 거래 활동 요약
    private Integer totalTradeCount;             // 총 거래 횟수 (시즌 종료 시 일괄 청산 기록 포함)
    private Integer buyOrderCount;               // 총 매수 주문 횟수
    private Integer sellOrderCount;              // 총 매도 주문 횟수
    private Long avgTradeAmount;           // 평균 거래 금액
    private Long totalRealizedProfitLoss;  // 총 매매 손익 (매도 금액 − 매수 금액. 완료 시즌은 청산 포함이라 = 총 손익)
    private Map<String, Long> coinRealizedProfitLoss; // 코인별 매매 손익
    private Map<String, Integer> coinTradeCounts; // 코인별 거래 횟수

    // 4. 투자 성향 관련 지표
    private String mostTradedCoinSymbol;       // 가장 많이 거래한 코인 심볼
    private Long mostTradedCoinTradeVolume; // 가장 많이 거래한 코인의 총 거래 금액
    private String highestProfitCoinSymbol;    // 가장 수익을 많이 낸 코인 심볼
    private Long highestProfitCoinAmount;  // 가장 수익을 많이 낸 코인의 수익 금액
    private String lowestProfitCoinSymbol;     // 가장 손실을 많이 낸 코인 심볼
    private Long lowestProfitCoinAmount;   // 가장 손실을 많이 낸 코인의 손실 금액
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
