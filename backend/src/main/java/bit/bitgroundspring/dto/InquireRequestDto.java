package bit.bitgroundspring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InquireRequestDto {
    private UserDto user;
    private String title;
    private String content;
}
