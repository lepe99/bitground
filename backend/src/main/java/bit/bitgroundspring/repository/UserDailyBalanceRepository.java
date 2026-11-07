// src/main/java/bit/bitgroundspring/repository/UserDailyBalanceRepository.java

package bit.bitgroundspring.repository;

import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.UserDailyBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserDailyBalanceRepository extends JpaRepository<UserDailyBalance, Integer> {

 // 추가된 메서드

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
