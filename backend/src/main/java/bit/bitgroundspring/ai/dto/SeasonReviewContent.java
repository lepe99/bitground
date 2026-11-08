package bit.bitgroundspring.ai.dto;

import bit.bitgroundspring.ai.SeasonReviewScorer;

import java.util.List;

/**
 * AI 시즌 리뷰 본문 (prompt_version = season-review-v2). ai_advices.advice에 JSON으로 저장하고 화면이 섹션별로 그린다
 *
 * @param headline       한 줄 총평
 * @param strengths      잘한 점
 * @param weaknesses     아쉬운 점
 * @param nextSeason     다음 시즌에 해 볼 행동과 이유
 * @param scoreReason    점수 설명 (점수 자체는 코드가 계산)
 * @param scoreBreakdown 점수 구성 요소 — 모델 출력이 아니라 저장 직전에 코드가 채운다
 */
public record SeasonReviewContent(
        String headline,
        List<String> strengths,
        List<String> weaknesses,
        List<NextAction> nextSeason,
        String scoreReason,
        List<SeasonReviewScorer.Part> scoreBreakdown
) {
    public record NextAction(String action, String reason) {
    }

    public SeasonReviewContent withScoreBreakdown(List<SeasonReviewScorer.Part> parts) {
        return new SeasonReviewContent(headline, strengths, weaknesses, nextSeason, scoreReason, parts);
    }
}
