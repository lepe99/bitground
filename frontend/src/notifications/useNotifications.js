import {useContext} from 'react';
import {NotificationContext} from './NotificationContext.js';

/**
 * 실시간 알림 상태
 * - unreadNotificationsCount / setUnreadNotificationsCount: 읽지 않은 알림 개수 (헤더 배지)
 * - userCash: 예약 주문 체결 알림으로 받은 최신 현금 (거래 화면이 자산을 다시 불러오는 신호)
 */
export const useNotifications = () => {
    const context = useContext(NotificationContext);
    if (!context) {
        throw new Error('useNotifications는 NotificationProvider 내부에서 사용되어야 합니다.');
    }
    return context;
};
