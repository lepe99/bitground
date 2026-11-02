package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.ReactionTargetType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionDto {
    private ReactionTargetType targetType;
    private Long targetId;
    private Boolean liked;
}
