package bit.bitgroundspring.support;

import bit.bitgroundspring.common.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RFC 9457 Problem Details 에러 응답 검증
 */
public final class ProblemMatchers {

    private ProblemMatchers() {
    }

    public static ResultMatcher problem(ErrorCode code) {
        return ResultMatcher.matchAll(
                status().is(code.getStatus().value()),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(code.getStatus().value()),
                jsonPath("$.code").value(code.name()),
                jsonPath("$.title").exists(),
                jsonPath("$.detail").exists(),
                jsonPath("$.instance").exists(),
                jsonPath("$.timestamp").exists()
        );
    }
}
