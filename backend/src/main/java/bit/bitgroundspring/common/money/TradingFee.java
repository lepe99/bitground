package bit.bitgroundspring.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 거래 수수료 (업비트 원화마켓과 같은 0.05%, 시장가·지정가 모두 같은 요율)
 *
 * - 매수: 주문 대금에 수수료를 더해 현금에서 뺀다
 * - 매도: 받는 대금에서 수수료를 빼고 현금에 더한다
 * - 수수료는 원 단위 올림 (사용자가 내는 돈은 올림이라는 Money 규칙과 같다)
 *
 * 화면(frontend/src/utils/tradingFee.js)도 같은 요율·반올림으로 예상 수수료를 보여 준다.
 * 상장 폐지 자동 정리·시즌 종료 청산처럼 시스템이 처리하는 거래에는 받지 않는다.
 */
public final class TradingFee {

    public static final BigDecimal RATE = new BigDecimal("0.0005");

    private TradingFee() {
    }

    /** 원 단위 거래 대금의 수수료 (올림) */
    public static long of(long tradeKrw) {
        if (tradeKrw <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(tradeKrw).multiply(RATE).setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /** 매수에 실제로 드는 현금 = 대금 + 수수료 */
    public static long buyCost(long tradeKrw) {
        return tradeKrw + of(tradeKrw);
    }

    /** 매도로 실제로 받는 현금 = 대금 − 수수료 */
    public static long sellProceeds(long tradeKrw) {
        return tradeKrw - of(tradeKrw);
    }
}
