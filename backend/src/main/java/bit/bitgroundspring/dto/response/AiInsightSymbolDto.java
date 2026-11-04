// src/main/java/bit/bitgroundspring/dto/AiInsightSymbolDto.java

package bit.bitgroundspring.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiInsightSymbolDto {
    private String symbol;
    private String koreanName;
    private String pickReason; // 스케줄러가 고른 이유 (거래대금 상위, 상승률 상위 등)
}
