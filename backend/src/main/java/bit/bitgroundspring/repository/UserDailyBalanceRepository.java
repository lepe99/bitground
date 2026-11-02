// src/main/java/bit/bitgroundspring/repository/UserDailyBalanceRepository.java

package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.UserDailyBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDailyBalanceRepository extends JpaRepository<UserDailyBalance, Integer> {

    /**
     * 특정 사용자, 시즌, 스냅샷 날짜에 해당하는 일별 잔고 스냅샷을 조회합니다.
     * `uq_user_daily_balances_user_season_snapshot` unique constraint에 의해 하나의 결과만 반환됩니다.
     *
     * @param user 조회할 User 엔티티
     * @param season 조회할 Season 엔티티
     * @param snapshotDate 조회할 스냅샷 날짜 (필드명과 일치)
     * @return 해당 UserDailyBalance 엔티티 (존재할 경우)
     */
    Optional<UserDailyBalance> findByUserAndSeasonAndSnapshotDate(User user, Season season, LocalDate snapshotDate);

    /**
     * 특정 사용자 및 스냅샷 날짜에 해당하는 일일 잔고 스냅샷을 조회합니다.
     * 이 메서드는 UserDailyBalance에 Season 정보가 없거나,
     * 특정 날짜에 대한 User의 총 잔고만 필요한 경우에 사용될 수 있습니다.
     *
     * @param user 조회할 User 엔티티
     * @param snapshotDate 조회할 스냅샷 날짜
     * @return 해당 UserDailyBalance 엔티티 (존재할 경우)
     */
    Optional<UserDailyBalance> findByUserAndSnapshotDate(User user, LocalDate snapshotDate); // 추가된 메서드

    /**
     * 특정 사용자 및 시즌에 대해 지정된 기간 내의 모든 일별 잔고 스냅샷을 조회합니다.
     *
     * @param user 조회할 User 엔티티
     * @param season 조회할 Season 엔티티
     * @param start 조회 시작 날짜
     * @param end 조회 종료 날짜
     * @return 기간 내의 UserDailyBalance 엔티티 목록 (snapshotDate 오름차순 정렬)
     */
    List<UserDailyBalance> findByUserAndSeasonAndSnapshotDateBetweenOrderBySnapshotDateAsc(User user, Season season, LocalDate start, LocalDate end);
}
