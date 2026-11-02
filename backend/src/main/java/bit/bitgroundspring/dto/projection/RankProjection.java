package bit.bitgroundspring.dto.projection;

import java.time.LocalDateTime;

public interface RankProjection {
    Integer getUserId();
    String getName();
    String getProfileImage();
    Integer getRanks();
    Integer getTier();
    Long getTotalValue();
    LocalDateTime getUpdatedAt();
    String getSeasonName();

    Integer getSeasonId();
}