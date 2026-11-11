package bit.bitgroundspring.ai;

import bit.bitgroundspring.ai.dto.InvestmentSummaryDto;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 시즌 리뷰 점수 (0~100). 모델에게 맡기면 같은 성적에도 점수가 들쭉날쭉하므로 정해진 규칙으로 코드가 계산하고,
 * 모델은 이 점수와 구성 요소를 받아 이유만 설명한다.
 *
 * <pre>
 * 순위      45점  1위 45 → 꼴찌 0 (참가자 1명이면 만점)
 * 시장 대비  20점  수익률 − 시장 지수(없으면 BTC) 등락: +10%p 이상 만점, −10%p 이하 0 (비교 기준이 없으면 수익률 자체로)
 * 낙폭 관리  20점  일별 총자산의 최대 낙폭: 0% 만점 → 25% 이상 0 (기록이 없으면 절반)
 * 매매 절제  15점  하루 평균 거래: 5회 이하 만점 → 20회 이상 0
 * </pre>
 */
public final class SeasonReviewScorer {

    public record Part(String label, int points, int max, String basis) {
    }

    public record Score(int total, List<Part> parts) {
    }

    private SeasonReviewScorer() {
    }

    public static Score score(InvestmentSummaryDto s) {
        List<Part> parts = new ArrayList<>();

        // 순위
        int participants = s.getParticipantCount() != null ? s.getParticipantCount() : 0;
        int rank = s.getFinalRank() != null ? s.getFinalRank() : participants;
        double rankRatio = participants <= 1 ? 1 : 1 - (double) (rank - 1) / (participants - 1);
        parts.add(new Part("순위", round(45 * clamp(rankRatio)), 45,
                participants > 0 ? String.format("%d위 / %d명", rank, participants) : "순위 기록 없음"));

        // 시장 대비
        Float returnPct = s.getTotalProfitLossPercentage();
        Float benchmark = s.getMarketIndexChangePercentage() != null ? s.getMarketIndexChangePercentage() : s.getBtcChangePercentage();
        String benchmarkName = s.getMarketIndexChangePercentage() != null ? "시장 지수" : "BTC";
        if (returnPct == null) {
            parts.add(new Part("시장 대비", 10, 20, "수익률 정보 없음"));
        } else if (benchmark == null) {
            parts.add(new Part("시장 대비", round(20 * clamp((returnPct + 10) / 20.0)), 20,
                    String.format("수익률 %+.2f%% (비교 기준 없음)", returnPct)));
        } else {
            double excess = returnPct - benchmark;
            parts.add(new Part("시장 대비", round(20 * clamp((excess + 10) / 20.0)), 20,
                    String.format("수익률 %+.2f%% vs %s %+.2f%% (%+.2f%%p)", returnPct, benchmarkName, benchmark, excess)));
        }

        // 낙폭 관리
        Float mdd = s.getMaxDrawdownPercentage();
        if (mdd == null) {
            parts.add(new Part("낙폭 관리", 10, 20, "일별 자산 기록 부족"));
        } else {
            parts.add(new Part("낙폭 관리", round(20 * clamp(1 - mdd / 25.0)), 20, String.format("최대 낙폭 -%.1f%%", mdd)));
        }

        // 매매 절제
        double perDay = s.getAvgTradesPerDay() != null ? s.getAvgTradesPerDay() : 0;
        parts.add(new Part("매매 절제", round(15 * clamp(1 - (perDay - 5) / 15.0)), 15, String.format("하루 평균 %.1f회 거래", perDay)));

        int total = parts.stream().mapToInt(Part::points).sum();
        return new Score(Math.max(0, Math.min(100, total)), parts);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }
}
