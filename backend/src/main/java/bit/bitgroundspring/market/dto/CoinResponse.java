package bit.bitgroundspring.market.dto;

import bit.bitgroundspring.market.Coin;

import java.time.LocalDateTime;

/**
 * 코인 응답. 엔티티를 직접 노출하지 않기 위한 DTO (필드명은 기존 응답과 동일)
 */
public record CoinResponse(
        Integer id,
        String symbol,
        String koreanName,
        Long tradePrice24h,
        Float changeRate,
        Boolean isCaution,
        Boolean isWarning,
        Boolean isDeleted,
        LocalDateTime updatedAt
) {
    public static CoinResponse from(Coin coin) {
        return new CoinResponse(coin.getId(), coin.getSymbol(), coin.getKoreanName(), coin.getTradePrice24h(),
                coin.getChangeRate(), coin.getIsCaution(), coin.getIsWarning(), coin.getIsDeleted(), coin.getUpdatedAt());
    }
}
