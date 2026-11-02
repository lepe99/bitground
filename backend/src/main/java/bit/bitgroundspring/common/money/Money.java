package bit.bitgroundspring.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액·수량 계산 규칙
 *
 * - 현금은 원 단위 정수(long). 사용자가 내는 돈은 올림, 받는 돈은 내림해 시스템이 돈을 만들어 내지 않는다.
 * - 가격은 소수 8자리, 수량은 소수 10자리 BigDecimal (DB DECIMAL과 같은 자릿수)
 */
public final class Money {

    public static final int PRICE_SCALE = 8;
    public static final int QUANTITY_SCALE = 10;

    private Money() {
    }

    /** 요청·외부 API의 double 값을 BigDecimal로 (이진 부동소수점 오차가 끼지 않도록 문자열 표현 기준) */
    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal price(BigDecimal value) {
        return value.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** 수량은 버림 (보유하지 않은 수량이 생기지 않도록) */
    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.DOWN);
    }

    /** 가격 × 수량을 원 단위로 올림 (매수 대금) */
    public static long krwCeil(BigDecimal price, BigDecimal quantity) {
        return price.multiply(quantity).setScale(0, RoundingMode.CEILING).longValueExact();
    }

    /** 가격 × 수량을 원 단위로 내림 (매도 대금) */
    public static long krwFloor(BigDecimal price, BigDecimal quantity) {
        return price.multiply(quantity).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** 원 단위 금액으로 살 수 있는 수량 (버림) */
    public static BigDecimal quantityFor(long krw, BigDecimal price) {
        return BigDecimal.valueOf(krw).divide(price, QUANTITY_SCALE, RoundingMode.DOWN);
    }

    /** 가중 평균 매입가: (기존 수량 × 기존 평균가 + 추가 수량 × 체결가) / 전체 수량 */
    public static BigDecimal averagePrice(BigDecimal heldQty, BigDecimal heldAvg, BigDecimal addQty, BigDecimal addPrice) {
        BigDecimal total = heldQty.multiply(heldAvg).add(addQty.multiply(addPrice));
        return total.divide(heldQty.add(addQty), PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
