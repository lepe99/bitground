package bit.bitgroundspring.dto;

import bit.bitgroundspring.entity.NotificationSetting;

/** 알림 설정. 바꿀 때 비워 둔(null) 항목은 그대로 둔다 */
public record NotificationSettingDto(Boolean orderExecution, Boolean comment, Boolean notice,
                                     Boolean seasonUpdate, Boolean toast) {

    public static NotificationSettingDto from(NotificationSetting s) {
        return new NotificationSettingDto(s.isOrderExecution(), s.isComment(), s.isNotice(), s.isSeasonUpdate(), s.isToast());
    }

    public void applyTo(NotificationSetting s) {
        if (orderExecution != null) s.setOrderExecution(orderExecution);
        if (comment != null) s.setComment(comment);
        if (notice != null) s.setNotice(notice);
        if (seasonUpdate != null) s.setSeasonUpdate(seasonUpdate);
        if (toast != null) s.setToast(toast);
    }
}
