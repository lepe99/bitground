package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TierDto {
    private int tier;
    private String tierName;
}