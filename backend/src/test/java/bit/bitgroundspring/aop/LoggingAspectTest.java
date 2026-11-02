package bit.bitgroundspring.aop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAspectTest {

    private LoggingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new LoggingAspect();
        ReflectionTestUtils.setField(aspect, "maxStringLength", 200);
        ReflectionTestUtils.setField(aspect, "maxCollectionSize", 3);
    }

    @Test
    void 이름이_민감한_파라미터는_마스킹한다() {
        String formatted = aspect.formatArguments(
                new String[]{"refreshToken", "secretKey", "seasonFlag"},
                new Object[]{"abc-real-token", "real-secret", "split"});

        assertThat(formatted)
                .contains("refreshToken=****", "secretKey=****", "seasonFlag=\"split\"")
                .doesNotContain("abc-real-token", "real-secret");
    }

    @Test
    void Map_안의_민감한_키도_마스킹한다() {
        assertThat(aspect.formatValue(Map.of("password", "p@ss")))
                .isEqualTo("{password=****}");
    }

    @Test
    void 파일은_내용_대신_메타데이터만_남긴다() {
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        assertThat(aspect.formatValue(file)).isEqualTo("MultipartFile(name=a.png, type=image/png, size=3)");
        assertThat(aspect.formatValue(new byte[10])).isEqualTo("byte[10]");
    }

    @Test
    void 긴_컬렉션과_문자열은_잘라서_남긴다() {
        assertThat(aspect.formatValue(List.of(1, 2, 3, 4, 5))).isEqualTo("[1, 2, 3, ... (2 more)]");
        assertThat(aspect.formatValue("x".repeat(300))).endsWith("...(302 chars)").hasSizeLessThan(230);
    }

    @Test
    void toString이_실패해도_예외를_던지지_않는다() {
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("lazy loading");
            }
        };
        assertThat(aspect.formatValue(broken)).isNotBlank();
    }
}
