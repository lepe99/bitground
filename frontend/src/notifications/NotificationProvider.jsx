import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {NotificationContext} from './NotificationContext.js';
import {formatNotification} from './formatNotification.js';
import {useAuth} from '../auth/useAuth.js';
import {useToast} from '../components/toastContext.js';
import {
    fetchNotificationSettings,
    fetchUnreadNotificationCount,
    markNotificationsRead,
    updateNotificationSettings,
} from '../api/notificationApi.js';
import {groupKey} from './groupNotifications.js';
import {targetOf} from './notificationKinds.js';
import {router} from '../routes/router.jsx';

const SSE_URL = `${import.meta.env.VITE_API_URL}/notifications/subscribe`;
const RECONNECT_BASE_MS = 3000;

// 설정을 못 불러왔을 때(설정한 적 없을 때와 같이) 모두 켬
const DEFAULT_SETTINGS = {orderExecution: true, comment: true, notice: true, seasonUpdate: true, toast: true};
// 알림 종류 → 설정 항목 (문의 알림은 끌 수 없음)
const SETTING_OF = {ORDER_EXECUTION: 'orderExecution', COMMENT: 'comment', NOTICE: 'notice', SEASON_UPDATE: 'seasonUpdate'};
// 연속 실패 시 재연결을 멈추는 횟수 (지수 백오프, 최대 30초 간격)
const MAX_RECONNECT_ATTEMPTS = 8;
const MAX_RECONNECT_DELAY_MS = 30000;

/**
 * 실시간 알림 (SSE)
 * 로그인한 동안에만 /notifications/subscribe에 연결하고, 받은 알림을 토스트로 띄우며 읽지 않은 개수를 갱신한다.
 * (기존: ToastProvider 안에 토스트 UI·SSE 연결·알림 개수·현금 상태가 함께 있었음)
 */
export const NotificationProvider = ({children}) => {
    const {isLoggedIn} = useAuth();
    const {addToast, showNotification} = useToast();
    const [userCash, setUserCash] = useState(0);
    const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);
    const [settings, setSettings] = useState(DEFAULT_SETTINGS);
    const settingsRef = useRef(DEFAULT_SETTINGS); // SSE 처리기에서 최신 설정을 읽는다
    settingsRef.current = settings;

    const eventSourceRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const reconnectAttemptsRef = useRef(0);
    const connectRef = useRef(() => {});
    const enabledRef = useRef(false); // 로그아웃 뒤 진행 중이던 재연결이 다시 붙지 않도록

    const refreshUnreadCount = useCallback(async () => {
        try {
            setUnreadNotificationsCount(await fetchUnreadNotificationCount());
        } catch (error) {
            console.error('읽지 않은 알림 개수 가져오기 실패:', error);
        }
    }, []);

    // 토스트를 누르면 합쳐진 알림을 모두 읽음으로 하고 해당 화면으로 이동
    const openFromToast = useCallback((items) => {
        const ids = items.map((i) => i.id).filter((id) => id > 0);
        if (ids.length > 0) {
            markNotificationsRead(ids)
                .then(refreshUnreadCount)
                .catch((error) => console.error('알림 읽음 처리 실패:', error));
        }
        const to = targetOf(items[0]);
        if (to) void router.navigate(to);
    }, [refreshUnreadCount]);

    const handleMessage = useCallback((event) => {
        try {
            const notification = JSON.parse(event.data);
            if (!notification.message) return;

            if (notification.message === 'ORDER_EXECUTION') {
                setUserCash(notification.data.cash);
            }
            const text = formatNotification(notification);
            // 공지·시즌은 모든 접속자에게 오므로 끈 종류는 여기서 거른다. 토스트를 끄면 배지만 바뀐다
            const muted = settingsRef.current[SETTING_OF[notification.message]] === false;
            if (muted) return;
            if (notification.messageType === 'ERROR') {
                addToast(text, 'error');
            } else if (text && settingsRef.current.toast) {
                const item = {
                    id: notification.data?.id, // 저장된 알림 번호 (공지·시즌은 없음)
                    messageType: notification.message,
                    message: text.trim(),
                    postId: notification.data?.postId,
                };
                showNotification({key: groupKey(item), item, onOpen: openFromToast});
            }
            refreshUnreadCount();
        } catch (e) {
            console.error('SSE 메시지 파싱 오류:', e);
        }
    }, [addToast, showNotification, openFromToast, refreshUnreadCount]);

    const connect = useCallback(() => {
        if (eventSourceRef.current) return;

        const eventSource = new EventSource(SSE_URL, {withCredentials: true});
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
            reconnectAttemptsRef.current = 0;
        };
        eventSource.onmessage = handleMessage;
        eventSource.onerror = () => {
            // 브라우저 기본 재연결은 401이어도 계속 시도하므로 직접 닫고 다시 연결한다
            eventSource.close();
            eventSourceRef.current = null;

            const attempt = ++reconnectAttemptsRef.current;
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                console.warn('SSE 재연결을 중단합니다.');
                return;
            }
            const delay = Math.min(RECONNECT_BASE_MS * 2 ** (attempt - 1), MAX_RECONNECT_DELAY_MS);
            reconnectTimeoutRef.current = setTimeout(async () => {
                // EventSource는 응답 상태 코드를 알려주지 않으므로 axios 요청으로 인증을 먼저 확인한다.
                // 액세스 토큰이 만료됐으면 인터셉터가 재발급하고, 재발급도 실패하면 로그아웃(forceLogout)되어
                // isLoggedIn이 false가 되면서 연결이 정리된다.
                try {
                    await fetchUnreadNotificationCount();
                    if (enabledRef.current) connectRef.current();
                } catch {
                    // 인증 실패: 로그아웃 처리에 맡기고 재연결하지 않음
                }
            }, delay);
        };
    }, [handleMessage]);
    connectRef.current = connect;

    const disconnect = useCallback(() => {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
        eventSourceRef.current?.close();
        eventSourceRef.current = null;
        reconnectAttemptsRef.current = 0;
    }, []);

    // 로그인한 동안에만 연결 (비로그인 상태에서 401 재연결이 반복되지 않도록)
    useEffect(() => {
        if (!isLoggedIn) {
            setUnreadNotificationsCount(0);
            setSettings(DEFAULT_SETTINGS);
            return;
        }

        enabledRef.current = true;
        connect();
        refreshUnreadCount();
        fetchNotificationSettings()
            .then((loaded) => setSettings({...DEFAULT_SETTINGS, ...loaded}))
            .catch((error) => console.error('알림 설정 가져오기 실패:', error));

        return () => {
            enabledRef.current = false;
            disconnect();
        };
    }, [isLoggedIn, connect, disconnect, refreshUnreadCount]);

    // 설정 바꾸기: 화면에 먼저 반영하고, 실패하면 되돌린다
    const updateSettings = useCallback(async (patch) => {
        const previous = settingsRef.current;
        setSettings({...previous, ...patch});
        try {
            setSettings({...DEFAULT_SETTINGS, ...(await updateNotificationSettings(patch))});
        } catch (error) {
            setSettings(previous);
            throw error;
        }
    }, []);

    const value = useMemo(
        () => ({userCash, unreadNotificationsCount, setUnreadNotificationsCount, refreshUnreadCount, settings, updateSettings}),
        [userCash, unreadNotificationsCount, refreshUnreadCount, settings, updateSettings]
    );

    return (
        <NotificationContext.Provider value={value}>
            {children}
        </NotificationContext.Provider>
    );
};
