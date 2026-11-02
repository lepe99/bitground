package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.MarketIndex;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketIndexDto {
    private String date;
    private String hour;
    private int altIndex;
    private int marketIndex;

    public static MarketIndexDto fromEntity(MarketIndex entity) {
        return MarketIndexDto.builder()
                .date(entity.getDate().toString())
                .hour(String.format("%02d:00", entity.getHour()))
                .altIndex(entity.getAltIndex())
                .marketIndex(entity.getMarketIndex())
                .build();
    }
}
