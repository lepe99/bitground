package bit.bitgroundspring.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 버전이 붙은 프롬프트 묶음 (classpath:prompts/{version}/).
 * <ul>
 *   <li>system.txt: 모델에 주는 규칙 (systemInstruction)</li>
 *   <li>generation.json: 응답 스키마 (generationConfig). temperature는 Gemini 3 계열이 무시해 두지 않는다</li>
 * </ul>
 * 프롬프트를 고칠 때는 기존 폴더를 고치지 않고 새 버전 폴더를 만든 뒤 설정(gemini.season-review.prompt-version)만 바꾼다.
 * 저장된 결과에 버전이 남으므로, 같은 평가셋으로 이전 버전과 나란히 비교하고 문제가 있으면 설정만 되돌린다.
 * 사용자별 데이터(<season> 블록)는 코드가 만든다.
 */
public record PromptTemplate(String version, String system, Map<String, Object> responseSchema) {

    @SuppressWarnings("unchecked")
    public static PromptTemplate load(String version, ObjectMapper objectMapper) {
        String base = "/prompts/" + version + "/";
        String system = read(base + "system.txt");
        try {
            Map<String, Object> generation = objectMapper.readValue(read(base + "generation.json"), Map.class);
            return new PromptTemplate(version, system, (Map<String, Object>) generation.get("responseSchema"));
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 설정을 읽지 못했습니다: " + base + "generation.json", e);
        }
    }

    private static String read(String path) {
        try (InputStream in = PromptTemplate.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("프롬프트 파일이 없습니다: classpath:" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽지 못했습니다: " + path, e);
        }
    }
}
