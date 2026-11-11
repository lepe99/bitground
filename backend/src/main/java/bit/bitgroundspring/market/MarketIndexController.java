package bit.bitgroundspring.market;

import bit.bitgroundspring.market.dto.MarketIndexDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/market-index")
public class MarketIndexController {
    private final MarketIndexService marketIndexService;

    public MarketIndexController(MarketIndexService marketIndexService) {
        this.marketIndexService = marketIndexService;
    }

    @GetMapping("/today")
    public List<MarketIndexDto> getToday() {
        return marketIndexService.getTodayIndices();
    }

    @GetMapping("/yesterday")
    public List<MarketIndexDto> getYesterday() {
        return marketIndexService.getYesterdayIndices();
    }
}
