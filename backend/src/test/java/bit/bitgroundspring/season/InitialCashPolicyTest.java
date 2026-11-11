package bit.bitgroundspring.season;

import bit.bitgroundspring.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 시즌 중간 가입자의 시작 현금은 DB의 스플릿 2 시작 시각 기준 */
class InitialCashPolicyTest extends IntegrationTest {

    @Autowired
    InitialCashPolicy policy;
    @Autowired
    SeasonRepository seasonRepository;

    private void splitAt(LocalDateTime at) {
        Season season = fixtures.activeSeason();
        season.setSplitStartsAt(at);
        seasonRepository.save(season);
    }

    @Test
    void 스플릿_2_전이면_1000만원() {
        splitAt(LocalDateTime.now().plusHours(1));
        assertThat(policy.getInitialCash()).isEqualTo(10_000_000);
    }

    @Test
    void 스플릿_2가_시작됐으면_2000만원() {
        splitAt(LocalDateTime.now().minusHours(1));
        assertThat(policy.getInitialCash()).isEqualTo(20_000_000);
    }

    @Test
    void 진행_중인_시즌이_없으면_1000만원() {
        assertThat(policy.getInitialCash()).isEqualTo(10_000_000);
    }
}
