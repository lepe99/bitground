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

public interface RankRepository extends JpaRepository<UserRanking, Long> {

    // 단일 사용자 + 시즌별 랭킹 조회
    Optional<UserRanking> findByUserAndSeason(User user, Season season);

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
    
    // 툴팁용: 시즌 엔티티 포함해서 최대 5개
    @Query("SELECT ur.season.name as name, ur.tier as tier " +
            "FROM UserRanking ur " +
            "WHERE ur.user = :user AND ur.season.status = :status " +
            "ORDER BY ur.season.id DESC")
    List<PastSeasonTierProjection> findTop5CompletedSeasonsByUser(
            @Param("user") User user,
            @Param("status") Status status,
            Pageable pageable
    );
    
    // 전체 시즌 중 최고 티어
    @Query("SELECT MAX(r.tier) FROM UserRanking r WHERE r.user = :user AND r.season.status <> 'PENDING'")
    Integer findHighestTierByUser(@Param("user") User user);
}