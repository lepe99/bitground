package bit.bitgroundspring.season.dto;

import bit.bitgroundspring.common.Status;

import java.time.LocalDate;

public interface SeasonProjection {
    Integer getId();
    String getName();
    LocalDate getStartAt();
    LocalDate getEndAt();
    Status getStatus();
}
