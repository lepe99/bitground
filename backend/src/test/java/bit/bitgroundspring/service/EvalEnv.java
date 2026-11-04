package bit.bitgroundspring.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 평가셋용 설정: 환경변수가 우선이고, 없으면 backend/.env (KEY=VALUE)에서 읽는다
 */
final class EvalEnv {

    private EvalEnv() {
    }

    static Map<String, String> load() throws IOException {
        Map<String, String> values = new HashMap<>();
        Path dotenv = Path.of(".env");
        if (Files.exists(dotenv)) {
            for (String line : Files.readAllLines(dotenv)) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) continue;
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(trimmed.substring(0, eq).trim(), value);
            }
        }
        System.getenv().forEach((k, v) -> {
            if (k.startsWith("GEMINI_")) values.put(k, v);
        });
        return values;
    }
}
