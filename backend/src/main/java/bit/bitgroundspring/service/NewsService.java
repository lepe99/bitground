package bit.bitgroundspring.service;

import bit.bitgroundspring.client.NaverNewsClient;
import bit.bitgroundspring.dto.response.NewsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NaverNewsClient naverNewsClient;

    /** 키워드 최신 뉴스. 검색어 강조용 <b> 태그만 남기고 나머지 태그는 제거한다 */
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
