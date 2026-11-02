package bit.bitgroundspring.api;

import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.repository.CoinRepository;
import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 메인 화면 코인 목록 (DB 정렬·LIMIT, 상장 폐지 코인 제외)
 */
class CoinApiTest extends IntegrationTest {

    @Autowired
    CoinRepository coinRepository;

    @BeforeEach
    void setUp() {
        // C1~C7: 거래대금·변동률이 번호 순으로 커짐, C8은 가장 크지만 상장 폐지
        for (int i = 1; i <= 8; i++) {
            Coin coin = fixtures.coin("KRW-C" + i);
            coin.setTradePrice24h(i * 1_000L);
            coin.setChangeRate((float) (i - 4));
            coin.setIsWarning(i == 2 || i == 8);
            coin.setIsDeleted(i == 8);
            coinRepository.save(coin);
        }
    }

    @Test
    @DisplayName("거래대금·상승률·하락률 상위 5개를 DB에서 정렬해 내려주고, 상장 폐지 코인은 뺀다")
    void top5() throws Exception {
        mvc.perform(get("/coins/high-trade-price"))
                .andExpect(jsonPath("$[*].symbol", contains("KRW-C7", "KRW-C6", "KRW-C5", "KRW-C4", "KRW-C3")));
        mvc.perform(get("/coins/price-increase"))
                .andExpect(jsonPath("$[*].symbol", contains("KRW-C7", "KRW-C6", "KRW-C5", "KRW-C4", "KRW-C3")));
        mvc.perform(get("/coins/price-decrease"))
                .andExpect(jsonPath("$[*].symbol", contains("KRW-C1", "KRW-C2", "KRW-C3", "KRW-C4", "KRW-C5")));
    }

    @Test
    @DisplayName("유의 종목·전체 목록·심볼 목록에도 상장 폐지 코인은 없다")
    void excludesDelisted() throws Exception {
        mvc.perform(get("/coins/caution")).andExpect(jsonPath("$[*].symbol", contains("KRW-C2")));
        mvc.perform(get("/coins")).andExpect(jsonPath("$[*].symbol", not(hasItem("KRW-C8"))));
        mvc.perform(get("/coins/symbols")).andExpect(jsonPath("$[*].symbol", not(hasItem("KRW-C8"))));
    }
}
