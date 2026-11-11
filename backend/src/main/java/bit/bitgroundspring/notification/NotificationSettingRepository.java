package bit.bitgroundspring.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Integer> {

    List<NotificationSetting> findByUserIdIn(Collection<Integer> userIds);
}
