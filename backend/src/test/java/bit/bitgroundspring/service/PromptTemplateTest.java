package bit.bitgroundspring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("버전 폴더의 규칙·응답 스키마를 읽는다")
    void loadsVersion() {
        PromptTemplate t = PromptTemplate.load(GeminiService.DEFAULT_PROMPT_VERSION, mapper);

        assertThat(t.version()).isEqualTo("season-review-v2");
        assertThat(t.system()).startsWith("너는 모의투자 게임 Bitground의 시즌 회고 코치다.");
        assertThat(t.responseSchema()).containsEntry("type", "OBJECT").containsKey("required");
    }

    @Test
    @DisplayName("없는 버전이면 바로 실패한다 (서비스 시작 시점에 드러나도록)")
    void missingVersion() {
        assertThatThrownBy(() -> PromptTemplate.load("no-such-version", mapper))
                .hasMessageContaining("no-such-version");
    }
}
