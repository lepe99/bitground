package bit.bitgroundspring.client;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 네이버 뉴스 검색 API 클라이언트 (NAVER API HUB, 네이버 클라우드 플랫폼)
 * 기존 NAVER Developers Center(openapi.naver.com)에서 이관: 호출 주소와 인증 헤더 이름이 다르고 응답 형식은 같다.
 * https://guide.ncloud-docs.com/docs/apihub-migration
 */
@Component
@RequiredArgsConstructor
public class NaverNewsClient {

    private static final String BASE_URL = "https://naverapihub.apigw.ntruss.com/search/v1/news";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${naver.news.client-id}")
    private String clientId;

    @Value("${naver.news.client-secret}")
    private String clientSecret;

    /** 최신순 뉴스 검색 결과의 items 배열 */
    public JsonNode search(String keyword, int display, int start) {
        // 쿼리 값은 한 번만 인코딩 (기존: URLEncoder로 인코딩한 뒤 URI 생성 시 한 번 더 인코딩돼 한글 검색이 깨짐)
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                .queryParam("query", keyword)
                .queryParam("display", display)
                .queryParam("start", start)
                .queryParam("sort", "date")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        RequestEntity<Void> request = RequestEntity.get(uri)
                .header("X-NCP-APIGW-API-KEY-ID", clientId)
                .header("X-NCP-APIGW-API-KEY", clientSecret)
                .build();
        try {
            String body = restTemplate.exchange(request, String.class).getBody();
            return objectMapper.readTree(body).path("items");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e);
        }
    }
}
