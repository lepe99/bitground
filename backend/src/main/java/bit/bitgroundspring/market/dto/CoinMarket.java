package bit.bitgroundspring.market.dto;

import bit.bitgroundspring.market.Coin;

/**
 * 원화 마켓 코인 한 줄 (스케줄러가 매시 업비트에서 받아 coins 테이블에 저장한 값).
 * 화면이 업비트 REST(/market/all, /ticker/all)를 직접 부르지 않고 이 목록을 쓴다:
 * 브라우저에서 보낸 업비트 요청(Origin 헤더 포함)은 약 10초에 1회로 제한되기 때문.
 * 현재가·실시간 등락률은 화면이 업비트 WebSocket으로 따로 받는다.
 */
public record CoinMarket(
        String symbol,
        String koreanName,
        Long tradePrice24h,
        Float changeRate,
        boolean warning,
        boolean caution
) {
    public static CoinMarket from(Coin coin) {
        return new CoinMarket(coin.getSymbol(), coin.getKoreanName(), coin.getTradePrice24h(), coin.getChangeRate(),
                Boolean.TRUE.equals(coin.getIsWarning()), Boolean.TRUE.equals(coin.getIsCaution()));
    }
}
