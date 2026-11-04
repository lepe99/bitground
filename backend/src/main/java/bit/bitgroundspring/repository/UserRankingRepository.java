package bit.bitgroundspring.repository;

import bit.bitgroundspring.dto.projection.PastSeasonTierProjection;
import bit.bitgroundspring.dto.projection.RankProjection;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.Status;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.UserRanking;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRankingRepository extends JpaRepository<UserRanking, Long> {

    // 단일 사용자 + 시즌별 랭킹 조회
    Optional<UserRanking> findByUserAndSeason(User user, Season season);

    // 시즌 랭킹 참가자 수 (순위를 '상위 몇 %'로 해석할 때)
    long countBySeason(Season season);

    // 특정 시즌의 전체 랭킹 목록 (Projection)
    @Query("SELECT ur.user.id as userId, " +
            "u.name as name, " +
            "u.profileImage as profileImage, " +
            "ur.ranks as ranks, " +
            "ur.tier as tier, " +
            "ur.totalValue as totalValue, " +
            "ur.updatedAt as updatedAt, " +
            "ur.season.id as seasonId, " +
            "ur.season.name as seasonName " +
            "FROM UserRanking ur " +
            "JOIN ur.user u " +
            "WHERE ur.season.id = :seasonId " +
            "ORDER BY ur.ranks ASC")
    List<RankProjection> findRankingsBySeasonId(@Param("seasonId") Integer seasonId);

    // 현재 시즌 랭킹 목록
    @Query("SELECT ur.user.id as userId, " +
            "u.name as name, " +
            "u.profileImage as profileImage, " +
            "ur.ranks as ranks, " +
            "ur.tier as tier, " +
            "ur.totalValue as totalValue, " +
            "ur.updatedAt as updatedAt, " +
            "ur.season.id as seasonId, " +
            "ur.season.name as seasonName " +
            "FROM UserRanking ur " +
            "JOIN ur.user u " +
            "WHERE ur.season.status = 'PENDING' " +
            "ORDER BY ur.ranks ASC")
    List<RankProjection> findCurrentSeasonRankings();
    
    // 툴팁용: 참여한 완료 시즌 중 최근 것부터 (개수는 Pageable로 제한). 시즌별 최종 순위·자산·참가자 수 포함
    @Query("SELECT ur.season.name as name, ur.tier as tier, ur.ranks as ranks, ur.totalValue as totalValue, " +
            "(SELECT COUNT(r2) FROM UserRanking r2 WHERE r2.season = ur.season) as participants " +
            "FROM UserRanking ur " +
            "WHERE ur.user = :user AND ur.season.status = :status " +
            "ORDER BY ur.season.id DESC")
    List<PastSeasonTierProjection> findTop5CompletedSeasonsByUser(
            @Param("user") User user,
            @Param("status") Status status,
            Pageable pageable
    );
    
    // 참여한 완료 시즌 수
    @Query("SELECT COUNT(ur) FROM UserRanking ur WHERE ur.user = :user AND ur.season.status = :status")
    long countSeasonsByUser(@Param("user") User user, @Param("status") Status status);

    // 전체 시즌 중 최고 티어
    @Query("SELECT MAX(r.tier) FROM UserRanking r WHERE r.user = :user AND r.season.status <> 'PENDING'")
    Integer findHighestTierByUser(@Param("user") User user);
}