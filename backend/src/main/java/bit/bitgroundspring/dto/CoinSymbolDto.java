package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CoinSymbolDto {
    private String symbol;
    private String koreanName;
}