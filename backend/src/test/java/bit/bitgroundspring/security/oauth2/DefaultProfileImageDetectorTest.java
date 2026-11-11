package bit.bitgroundspring.security.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DefaultProfileImageDetectorTest {

    private static final String PHOTO = "https://example.com/me.jpg";

    private MockRestServiceServer server;
    private DefaultProfileImageDetector detector;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        detector = new DefaultProfileImageDetector(restTemplate);
    }

    @Test
    void 네이버_기본_사진_주소면_저장하지_않는다() {
        assertThat(detector.keepIfCustom("naver", "http://ssl.pstatic.net/static/pwe/address/img_profile.png", "t")).isNull();
        assertThat(detector.keepIfCustom("naver", PHOTO, "t")).isEqualTo(PHOTO);
    }

    @Test
    void 카카오는_is_default_image로_판단한다() {
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("{\"kakao_account\":{\"profile\":{\"is_default_image\":true}}}", MediaType.APPLICATION_JSON));
        assertThat(detector.keepIfCustom("kakao", PHOTO, "token")).isNull();
        server.verify();
    }

    @Test
    void 구글은_대표_사진의_default로_판단한다() {
        server.expect(requestTo("https://people.googleapis.com/v1/people/me?personFields=photos"))
                .andRespond(withSuccess("{\"photos\":[{\"metadata\":{\"primary\":false},\"url\":\"a\"},"
                        + "{\"metadata\":{\"primary\":true},\"url\":\"b\",\"default\":true}]}", MediaType.APPLICATION_JSON));
        assertThat(detector.keepIfCustom("google", PHOTO, "token")).isNull();
    }

    @Test
    void 확인_API가_실패하면_받은_사진을_그대로_쓴다() {
        server.expect(requestTo("https://people.googleapis.com/v1/people/me?personFields=photos")).andRespond(withServerError());
        assertThat(detector.keepIfCustom("google", PHOTO, "token")).isEqualTo(PHOTO);
    }

    @Test
    void 사진이_없으면_API를_부르지_않는다() {
        assertThat(detector.keepIfCustom("kakao", null, "token")).isNull();
        server.verify();
    }
}
