package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.Status;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonDto {
    
    private Integer id;
    private String name;
    private LocalDate startAt;
    private LocalDate endAt;
    private Status status;
    
}