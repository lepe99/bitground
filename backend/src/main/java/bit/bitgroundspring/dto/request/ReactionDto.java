package bit.bitgroundspring.dto.request;

import jakarta.validation.constraints.NotNull;
import bit.bitgroundspring.entity.ReactionTargetType;
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
