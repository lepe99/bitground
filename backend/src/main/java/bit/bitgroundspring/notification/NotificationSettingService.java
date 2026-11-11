package bit.bitgroundspring.notification;

import bit.bitgroundspring.notification.dto.NotificationSettingDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 알림 설정 (종류별 받기, 실시간 토스트). 설정한 적 없으면 모두 켬 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository repository;

    @Transactional(readOnly = true)
    public NotificationSettingDto get(Integer userId) {
        return NotificationSettingDto.from(repository.findById(userId).orElseGet(() -> NotificationSetting.defaults(userId)));
    }

    @Transactional
    public NotificationSettingDto update(Integer userId, NotificationSettingDto request) {
        NotificationSetting setting = repository.findById(userId).orElseGet(() -> NotificationSetting.defaults(userId));
        request.applyTo(setting);
        return NotificationSettingDto.from(repository.save(setting));
    }

    /** 받는 사람 중 이 종류의 알림을 끄지 않은 사람만 */
    @Transactional(readOnly = true)
    public Set<Integer> receivers(Message type, Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Map<Integer, NotificationSetting> settings = repository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(NotificationSetting::getUserId, Function.identity()));
        return userIds.stream()
                .filter(id -> !settings.containsKey(id) || settings.get(id).receives(type))
                .collect(Collectors.toSet());
    }
}
