package bit.bitgroundspring.controller;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URLEncoder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${naver.news.client-id}")
    private String naverClientId;

    @Value("${naver.news.client-secret}")
    private String naverClientSecret;

    private String cleanHTMLExceptBold(String html) {
        return html.replaceAll("<(?!/?b>)[^>]+>", ""); // <b> 태그만 유지
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getNews(
        @RequestParam("keyword") String keyword,
        @RequestParam(value ="display", defaultValue = "10") int display,
        @RequestParam(value="start", defaultValue = "1") int start
    ) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());

            URI uri = UriComponentsBuilder
                    .fromUriString("https://openapi.naver.com")
                    .path("/v1/search/news.json")
                    .queryParam("query", encodedKeyword)
                    .queryParam("display", display)
                    .queryParam("start", start)
                    .queryParam("sort", "date")
                    .build()
                    .toUri();
            
            RequestEntity<Void> req = RequestEntity
                    .get(uri)
                    .header("X-Naver-Client-Id", naverClientId)
                    .header("X-Naver-Client-Secret", naverClientSecret)
                    .build();

            ResponseEntity<String> result = restTemplate.exchange(req, String.class);

            JsonNode items = objectMapper.readTree(result.getBody()).path("items");

            List<Map<String, Object>> list = new ArrayList<>();

            for (JsonNode item : items) {
                Map<String, Object> news = new HashMap<>();

                news.put("title", cleanHTMLExceptBold(item.path("title").asText()));
                news.put("originallink", item.path("originallink").asText());
                news.put("link", item.path("link").asText());
                news.put("description", cleanHTMLExceptBold(item.path("description").asText()));
                news.put("pubDate", item.path("pubDate").asText());

                list.add(news);
            }

            return ResponseEntity.ok(list);

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e);
        }
    }
}