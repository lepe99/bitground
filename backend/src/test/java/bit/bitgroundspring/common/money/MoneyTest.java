package bit.bitgroundspring.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    @DisplayName("매수 대금은 올림, 매도 대금은 내림 — 사고팔기를 반복해도 현금이 생겨나지 않는다")
    void roundingNeverCreatesMoney() {
        BigDecimal price = new BigDecimal("0.37");      // 1원 미만 코인
        BigDecimal qty = new BigDecimal("3.3333333333");

        long paid = Money.krwCeil(price, qty);          // 1.233...원
        long received = Money.krwFloor(price, qty);

        assertThat(paid).isEqualTo(2);
        assertThat(received).isEqualTo(1);
        assertThat(received).isLessThanOrEqualTo(paid);
    }

    @Test
    @DisplayName("금액으로 살 수 있는 수량은 소수 10자리에서 버린다")
    void quantityForBudget() {
        assertThat(Money.quantityFor(100_000, new BigDecimal("150000000")))
                .isEqualByComparingTo("0.0006666666");
    }

    @Test
    @DisplayName("double 입력은 이진 오차 없이 변환한다 (0.1 + 0.2 ≠ 0.30000000000000004)")
    void fromDouble() {
        assertThat(Money.of(0.1).add(Money.of(0.2))).isEqualByComparingTo("0.3");
        assertThat(Money.quantity(Money.of(0.0024282084000000001))).isEqualByComparingTo("0.0024282084");
    }

    @Test
    @DisplayName("평균 매입가는 수량 가중 평균")
    void averagePrice() {
        BigDecimal avg = Money.averagePrice(new BigDecimal("1"), new BigDecimal("100"),
                new BigDecimal("3"), new BigDecimal("200"));
        assertThat(avg).isEqualByComparingTo("175");
    }
}
