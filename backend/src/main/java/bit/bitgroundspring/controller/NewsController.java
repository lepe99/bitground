package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.response.NewsResponse;
import bit.bitgroundspring.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public List<NewsResponse> getNews(@RequestParam("keyword") String keyword,
                                      @RequestParam(value = "display", defaultValue = "10") int display,
                                      @RequestParam(value = "start", defaultValue = "1") int start) {
        return newsService.search(keyword, Math.min(display, 100), Math.max(start, 1));
    }
}
