package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.MarketIndexDto;
import bit.bitgroundspring.entity.MarketIndex;
import bit.bitgroundspring.repository.MarketIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketIndexService {
    private final MarketIndexRepository marketIndexRepository;

    public List<MarketIndexDto> getTodayIndices() {
        LocalDate today = LocalDate.now();
        List<MarketIndex> todayData = marketIndexRepository.findByDate(today);

        return todayData.stream()
                .map(MarketIndexDto::fromEntity)
                .collect(Collectors.toList());
    }

    public List<MarketIndexDto> getYesterdayIndices() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<MarketIndex> yesterdayData = marketIndexRepository.findByDate(yesterday);

        return yesterdayData.stream()
                .map(MarketIndexDto::fromEntity)
                .collect(Collectors.toList());
    }
}
