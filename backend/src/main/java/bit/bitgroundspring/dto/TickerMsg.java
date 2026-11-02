package bit.bitgroundspring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TickerMsg {
    @JsonProperty("code")
    private String market;         // 예: "KRW-BTC"
    @JsonProperty("trade_price")
    private Float tradePrice;      // 체결 가격
}