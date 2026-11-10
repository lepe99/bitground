package bit.bitgroundspring.season.dto;

import java.time.LocalDateTime;

/**
 * 랭킹 한 줄. 조회 결과를 캐시(JSON)에 담을 수 있게 프로젝션({@link RankProjection})을 값으로 옮긴 것.
 * 응답 JSON의 필드 이름은 프로젝션과 같다.
 */
public record RankEntry(
        Integer userId,
        String name,
        String profileImage,
        Integer ranks,
        Integer tier,
        Long totalValue,
        LocalDateTime updatedAt,
        String seasonName,
        Integer seasonId
) {
    public static RankEntry from(RankProjection p) {
        return new RankEntry(p.getUserId(), p.getName(), p.getProfileImage(), p.getRanks(), p.getTier(),
                p.getTotalValue(), p.getUpdatedAt(), p.getSeasonName(), p.getSeasonId());
    }
}
