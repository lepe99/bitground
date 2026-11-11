package bit.bitgroundspring.market;

import bit.bitgroundspring.config.CacheConfig;
import bit.bitgroundspring.market.dto.NewsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NaverNewsClient naverNewsClient;

    /**
     * 키워드 최신 뉴스. 검색어 강조용 <b> 태그만 남기고 나머지 태그는 제거한다.
     * 같은 코인 화면을 여러 사람이 열면 같은 검색이 반복되므로 10분간 재사용한다 (네이버 API 하루 호출 한도).
     * 외부 API 오류는 예외라 캐시되지 않는다
     */
    @Cacheable(cacheNames = CacheConfig.NEWS, key = "#keyword.strip() + ':' + #display + ':' + #start")
    public List<NewsResponse> search(String keyword, int display, int start) {
        List<NewsResponse> result = new ArrayList<>();
        for (JsonNode item : naverNewsClient.search(keyword, display, start)) {
            result.add(new NewsResponse(
                    keepBoldOnly(item.path("title").asText()),
                    item.path("originallink").asText(),
                    item.path("link").asText(),
                    keepBoldOnly(item.path("description").asText()),
                    item.path("pubDate").asText()));
        }
        return result;
    }

    private static String keepBoldOnly(String html) {
        return html.replaceAll("<(?!/?b>)[^>]+>", "");
    }
}
