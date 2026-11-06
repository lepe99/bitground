package bit.bitgroundspring.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** SSE 전송 실패 중 사용자가 이미 연결을 끊은 경우(정상)를 구분해 로그 수준을 낮춘다 */
class UserSseEmittersTest {

    @Test
    void 끊긴_연결로_보낸_오류는_클라이언트가_떠난_것으로_본다() {
        assertThat(UserSseEmitters.isClientGone(new IOException("Broken pipe"))).isTrue();
        assertThat(UserSseEmitters.isClientGone(new AsyncRequestNotUsableException("ServletOutputStream failed to flush"))).isTrue();
        assertThat(UserSseEmitters.isClientGone(new RuntimeException("wrapped", new IOException("Connection reset by peer")))).isTrue();
        assertThat(UserSseEmitters.isClientGone(new IllegalStateException("ResponseBodyEmitter has already completed"))).isTrue();
    }

    @Test
    void 그_밖의_오류는_서버_문제로_본다() {
        assertThat(UserSseEmitters.isClientGone(new IllegalArgumentException("직렬화 실패"))).isFalse();
        assertThat(UserSseEmitters.isClientGone(new NullPointerException())).isFalse();
    }
}
