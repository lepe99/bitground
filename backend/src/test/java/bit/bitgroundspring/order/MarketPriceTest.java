package bit.bitgroundspring.order;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.service.MarketPriceService;
import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 매매에 쓰는 현재가: 실시간 시세로 받아 둔 값을 쓰고, 없을 때만 업비트 REST를 (제한해서) 부른다 */
class MarketPriceTest extends IntegrationTest {

    @Autowired
    MarketPriceService marketPriceService;

    @BeforeEach
    void setUp() {
        doReturn(ResponseEntity.ok(List.of(Map.of("trade_price", 90_000_000))))
                .when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    private void verifyRestCalls(int times) {
        verify(restTemplate, times(times)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void 실시간_시세를_받고_있으면_업비트_REST를_부르지_않는다() {
        marketPriceService.record("KRW-BTC", new BigDecimal("103621000"), 2_000);

        assertThat(marketPriceService.currentPrice("KRW-BTC")).isEqualByComparingTo("103621000");
        verifyRestCalls(0);
    }

    @Test
    void 늦게_처리된_예전_시세는_새_시세를_덮지_않는다() {
        marketPriceService.record("KRW-BTC", new BigDecimal("103621000"), 2_000);
        marketPriceService.record("KRW-BTC", new BigDecimal("100000000"), 1_000);

        assertThat(marketPriceService.currentPrice("KRW-BTC")).isEqualByComparingTo("103621000");
    }

    @Test
    void 받아_둔_값이_없으면_REST를_부르고_잠깐_재사용한다() {
        assertThat(marketPriceService.currentPrice("KRW-ETH")).isEqualByComparingTo("90000000");
        assertThat(marketPriceService.currentPrice("KRW-ETH")).isEqualByComparingTo("90000000");

        verifyRestCalls(1);
    }

    @Test
    void REST_호출이_한도를_넘으면_주문을_받지_않는다() {
        // 서로 다른 종목을 한도보다 훨씬 많이 연달아 조회 (초가 바뀌는 경계에 걸려도 넘도록 충분히)
        BusinessException error = null;
        for (int i = 0; i < 40 && error == null; i++) {
            try {
                marketPriceService.currentPrice("KRW-C" + i);
            } catch (BusinessException e) {
                error = e;
            }
        }
        assertThat(error).isNotNull();
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }
}
