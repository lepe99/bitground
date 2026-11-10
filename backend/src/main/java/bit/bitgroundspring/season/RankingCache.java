package bit.bitgroundspring.season;

import bit.bitgroundspring.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 랭킹 캐시 비우기. 랭킹 결과가 바뀌는 때에 부른다
 * - 스케줄러의 매시 랭킹 갱신·시즌 정산·스플릿 알림
 * - 닉네임·프로필 사진 변경 (랭킹에 함께 보이므로)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCache {

    private final CacheManager cacheManager;

    public void evictAll() {
        Cache cache = cacheManager.getCache(CacheConfig.RANKINGS);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
        } catch (RuntimeException e) {
            // 비우지 못한 값은 TTL(1시간)이 지나면 사라진다
            log.warn("[cache] 랭킹 캐시 비우기 실패: {}", e.toString());
        }
    }

    /**
     * 진행 중인 트랜잭션이 커밋된 뒤에 비운다.
     * 커밋 전에 비우면 그 사이 들어온 조회가 아직 바뀌기 전 값을 다시 캐시에 넣을 수 있다
     */
    public void evictAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictAll();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictAll();
            }
        });
    }
}
