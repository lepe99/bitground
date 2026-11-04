package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.InvestmentSummaryDto;
import bit.bitgroundspring.dto.SeasonReviewContent;
import bit.bitgroundspring.entity.UserDailyBalance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AI 시즌 리뷰 평가셋. 고정 입력(src/test/resources/eval/season-review.json)을 실제 Gemini로 여러 번 돌려
 * 자동 지표와 사람 평가용 원문을 build/reports/ai-eval에 남긴다.
 * 프롬프트·모델을 바꿀 때마다 같은 입력으로 돌려 보고서를 비교한다.
 *
 * <p>일반 테스트에서는 빠진다 (@Tag("eval")). 실행: {@code ./gradlew aiEval [-PevalRuns=3] [-PevalDelayMs=4000] [-PpromptVersion=season-review-v2]}
 * GEMINI_API_KEY·GEMINI_MODEL은 환경변수 또는 backend/.env에서 읽는다.
 *
 * <p>지표
 * <ul>
 *   <li>첫 시도 통과율: 운영 검증(GeminiService.validate)을 재시도 없이 통과한 비율</li>
 *   <li>숫자 일치율: 본문의 숫자(%, 원, 배, 위, 명, 회, 점)가 입력 프롬프트에 있는 비율</li>
 *   <li>항목 수 준수: 잘한 점·아쉬운 점·다음 시즌 각 2~3개, 총평 40자 이내</li>
 *   <li>점수 언급 일치: 점수 이유에 계산된 총점이 그대로 나오는지</li>
 *   <li>말로 쓴 숫자·마크다운 기호, 응답 시간</li>
 * </ul>
 */
@Tag("eval")
class SeasonReviewEval {

    // 본문의 숫자 + 단위 ('1,840,000원', '-27.35%', '2.0배', '38위' …)
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)(%p|%|원|배|위|명|회|점)");
    private static final Pattern SPELLED = Pattern.compile("퍼센트|(플러스|마이너스)\\s?\\d");
    private static final Pattern MARKDOWN = Pattern.compile("\\*\\*|^#|\\n- ", Pattern.MULTILINE);

    record EvalCase(String name, String note, InvestmentSummaryDto summary, List<List<Object>> balances) {
    }

    record RunResult(String caseName, int run, long millis, String error, SeasonReviewContent content,
                     String validation, int numbers, int numbersMatched, List<String> unmatched,
                     boolean countsOk, boolean scoreMentioned, boolean spelled, boolean markdown, int chars) {
    }

    @Test
    void run() throws Exception {
        Map<String, String> env = EvalEnv.load();
        String apiKey = env.get("GEMINI_API_KEY");
        String model = env.getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY가 없어 평가를 건너뜀");
        int runs = Integer.parseInt(System.getProperty("eval.runs", "2"));
        // 호출 사이 간격: 연속으로 부르면 요청 한도(429)에 걸려 평가가 아니라 한도를 재게 된다
        long delayMs = Long.parseLong(System.getProperty("eval.delayMs", "4000"));

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<EvalCase> cases;
        try (InputStream in = getClass().getResourceAsStream("/eval/season-review.json")) {
            cases = List.of(mapper.readValue(in, EvalCase[].class));
        }
        // -PpromptVersion=season-review-v3 처럼 바꿔 같은 입력으로 버전을 비교한다
        String promptVersion = System.getProperty("eval.promptVersion", GeminiService.DEFAULT_PROMPT_VERSION);
        GeminiService gemini = new GeminiService(model, apiKey, promptVersion, mapper);

        List<RunResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            InvestmentSummaryDto s = c.summary();
            s.setDailyBalanceTrend(c.balances().stream().map(b -> UserDailyBalance.builder()
                    .snapshotDate(LocalDate.parse((String) b.get(0)))
                    .totalValue(((Number) b.get(1)).longValue())
                    .cashBalance(((Number) b.get(2)).longValue())
                    .build()).toList());
            SeasonReviewScorer.Score score = SeasonReviewScorer.score(s);
            String prompt = GeminiService.buildPrompt(s, score);

            for (int run = 1; run <= runs; run++) {
                if (!results.isEmpty()) Thread.sleep(delayMs);
                long started = System.currentTimeMillis();
                try {
                    SeasonReviewContent content = gemini.requestReview(prompt);
                    results.add(evaluate(c.name(), run, System.currentTimeMillis() - started, content, prompt, score));
                } catch (Exception e) {
                    results.add(new RunResult(c.name(), run, System.currentTimeMillis() - started, e.getMessage(),
                            null, null, 0, 0, List.of(), false, false, false, false, 0));
                }
                System.out.printf("[eval] %s #%d 완료%n", c.name(), run);
            }
        }

        Path dir = Path.of("build", "reports", "ai-eval");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path report = dir.resolve("season-review-" + promptVersion + "-" + stamp + ".md");
        Files.writeString(report, report(model, promptVersion, runs, cases, results, mapper));
        Files.writeString(dir.resolve("season-review-" + promptVersion + "-" + stamp + "-human.csv"), humanSheet(results));
        System.out.println("[eval] 보고서: " + report.toAbsolutePath());
    }

    static RunResult evaluate(String name, int run, long millis, SeasonReviewContent c, String prompt, SeasonReviewScorer.Score score) {
        String validation = GeminiService.validate(c, prompt);
        String text = allText(c);

        String normalizedPrompt = prompt.replace(",", "");
        int numbers = 0, matched = 0;
        List<String> unmatched = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            numbers++;
            String value = m.group(1).replace(",", "");
            String unit = m.group(2);
            // 점수 구성은 프롬프트에 '42/45' 형태로 들어가므로 '점'은 숫자만 맞으면 인정
            boolean ok = unit.equals("점")
                    ? Pattern.compile("(?<![\\d.])" + Pattern.quote(value) + "(?![\\d.])").matcher(normalizedPrompt).find()
                    : normalizedPrompt.contains(value + unit);
            if (ok) matched++;
            else unmatched.add(m.group());
        }

        boolean countsOk = c.headline() != null && c.headline().length() <= 40
                && inRange(c.strengths()) && inRange(c.weaknesses()) && c.nextSeason() != null
                && c.nextSeason().size() >= 2 && c.nextSeason().size() <= 3;
        boolean scoreMentioned = c.scoreReason() != null && c.scoreReason().contains(String.valueOf(score.total()));
        return new RunResult(name, run, millis, null, c, validation, numbers, matched, unmatched, countsOk, scoreMentioned,
                SPELLED.matcher(text).find(), MARKDOWN.matcher(text).find(), text.length());
    }

    private static boolean inRange(List<String> items) {
        return items != null && items.size() >= 2 && items.size() <= 3;
    }

    private static String allText(SeasonReviewContent c) {
        StringBuilder b = new StringBuilder();
        b.append(c.headline()).append('\n').append(c.scoreReason()).append('\n');
        if (c.strengths() != null) c.strengths().forEach(s -> b.append(s).append('\n'));
        if (c.weaknesses() != null) c.weaknesses().forEach(s -> b.append(s).append('\n'));
        if (c.nextSeason() != null) c.nextSeason().forEach(a -> b.append(a.action()).append(' ').append(a.reason()).append('\n'));
        return b.toString();
    }

    private static String report(String model, String promptVersion, int runs, List<EvalCase> cases, List<RunResult> results, ObjectMapper mapper) throws Exception {
        List<RunResult> ok = results.stream().filter(r -> r.error() == null).toList();
        int numbers = ok.stream().mapToInt(RunResult::numbers).sum();
        int matched = ok.stream().mapToInt(RunResult::numbersMatched).sum();
        List<Long> times = ok.stream().map(RunResult::millis).sorted().toList();

        StringBuilder b = new StringBuilder();
        b.append("# AI 시즌 리뷰 평가 보고서\n\n");
        b.append("- 실행: ").append(LocalDateTime.now().withNano(0)).append('\n');
        b.append("- 모델: `").append(model).append("` / 프롬프트: `").append(promptVersion).append("`\n");
        b.append("- 케이스 ").append(cases.size()).append("개 × ").append(runs).append("회 = ").append(results.size()).append("회 호출\n\n");

        b.append("## 요약\n\n| 지표 | 값 |\n|---|---|\n");
        b.append(row("호출 성공", ok.size(), results.size()));
        b.append(row("첫 시도 검증 통과", count(ok, r -> r.validation() == null), ok.size()));
        b.append("| 숫자 일치율 | ").append(percent(matched, numbers)).append(" (").append(matched).append('/').append(numbers).append(") |\n");
        b.append(row("항목 수·총평 길이 준수", count(ok, RunResult::countsOk), ok.size()));
        b.append(row("점수 이유에 총점 그대로 언급", count(ok, RunResult::scoreMentioned), ok.size()));
        b.append(row("숫자를 말로 풀어 씀 (낮을수록 좋음)", count(ok, RunResult::spelled), ok.size()));
        b.append(row("마크다운 기호 (낮을수록 좋음)", count(ok, RunResult::markdown), ok.size()));
        if (!times.isEmpty()) {
            b.append("| 응답 시간 중앙값 / 최대 | ").append(times.get(times.size() / 2)).append("ms / ").append(times.get(times.size() - 1)).append("ms |\n");
            b.append("| 평균 글자 수 | ").append((int) ok.stream().mapToInt(RunResult::chars).average().orElse(0)).append(" |\n");
        }

        b.append("\n## 케이스별\n\n| 케이스 | 회차 | 검증 | 숫자 | 항목 수 | 총점 언급 | 비고 |\n|---|---|---|---|---|---|---|\n");
        for (RunResult r : results) {
            if (r.error() != null) {
                b.append("| ").append(r.caseName()).append(" | ").append(r.run()).append(" | 호출 실패 | | | | ").append(oneLine(r.error())).append(" |\n");
                continue;
            }
            b.append("| ").append(r.caseName()).append(" | ").append(r.run()).append(" | ")
                    .append(r.validation() == null ? "통과" : "거절: " + r.validation()).append(" | ")
                    .append(r.numbersMatched()).append('/').append(r.numbers()).append(" | ")
                    .append(r.countsOk() ? "O" : "X").append(" | ").append(r.scoreMentioned() ? "O" : "X").append(" | ")
                    .append(r.unmatched().isEmpty() ? "" : "입력에 없는 숫자: " + String.join(", ", r.unmatched()))
                    .append(" |\n");
        }

        b.append("\n## 원문 (사람 평가용)\n\n정확성·유용성·톤을 1~5점으로 매겨 같은 이름의 `-human.csv`에 적는다.\n");
        for (EvalCase c : cases) {
            b.append("\n### ").append(c.name()).append("\n\n> ").append(c.note()).append("\n");
            for (RunResult r : results) {
                if (!r.caseName().equals(c.name()) || r.content() == null) continue;
                b.append("\n**").append(r.run()).append("회차**\n\n```json\n")
                        .append(mapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(r.content()))
                        .append("\n```\n");
            }
        }
        return b.toString();
    }

    private static String humanSheet(List<RunResult> results) {
        // 앞의 BOM(U+FEFF)은 엑셀이 한글 CSV를 UTF-8로 읽게 한다
        StringBuilder b = new StringBuilder().appendCodePoint(0xFEFF).append("case,run,정확성(1-5),유용성(1-5),톤(1-5),메모\n");
        results.forEach(r -> b.append(r.caseName()).append(',').append(r.run()).append(",,,,\n"));
        return b.toString();
    }

    private static String row(String label, long value, long total) {
        return "| " + label + " | " + percent(value, total) + " (" + value + "/" + total + ") |\n";
    }

    private static String percent(long value, long total) {
        return total == 0 ? "-" : String.format("%.0f%%", value * 100.0 / total);
    }

    private static long count(List<RunResult> list, java.util.function.Predicate<RunResult> p) {
        return list.stream().filter(p).count();
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace("|", "/");
    }
}
