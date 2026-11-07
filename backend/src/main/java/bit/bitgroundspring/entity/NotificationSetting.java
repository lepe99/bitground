package bit.bitgroundspring.entity;

import jakarta.persistence.*;
import lombok.*;

/** 사용자별 알림 설정. 행이 없으면 모두 켠 것으로 본다 */
@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "order_execution", nullable = false)
    @Builder.Default
    private boolean orderExecution = true;

    @Column(name = "comment", nullable = false)
    @Builder.Default
    private boolean comment = true;

    @Column(name = "notice", nullable = false)
    @Builder.Default
    private boolean notice = true;

    @Column(name = "season_update", nullable = false)
    @Builder.Default
    private boolean seasonUpdate = true;

    /** 실시간 토스트 표시 (끄면 알림은 쌓이고 배지만 바뀐다) */
    @Column(name = "toast", nullable = false)
    @Builder.Default
    private boolean toast = true;

    public static NotificationSetting defaults(Integer userId) {
        return NotificationSetting.builder().userId(userId).build();
    }

    /** 이 종류의 알림을 받는지. 문의 알림(관리자 업무)은 끌 수 없다 */
    public boolean receives(Message type) {
        return switch (type) {
            case ORDER_EXECUTION -> orderExecution;
            case COMMENT -> comment;
            case NOTICE -> notice;
            case SEASON_UPDATE -> seasonUpdate;
            case INQUIRY -> true;
        };
    }
}
