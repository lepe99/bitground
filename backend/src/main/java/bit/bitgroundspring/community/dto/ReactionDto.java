package bit.bitgroundspring.community.dto;

import jakarta.validation.constraints.NotNull;
import bit.bitgroundspring.community.ReactionTargetType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionDto {
    @NotNull
    private ReactionTargetType targetType;
    @NotNull
    private Long targetId;
    @NotNull
    private Boolean liked;
}
