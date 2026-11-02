package bit.bitgroundspring.dto.projection;

import bit.bitgroundspring.entity.Status;

import java.time.LocalDate;

public interface SeasonProjection {
    Integer getId();
    String getName();
    LocalDate getStartAt();
    LocalDate getEndAt();
    Status getStatus();
}
