import React, {createContext, useContext, useState, useCallback, useEffect, useRef} from 'react';
import {IoIosCloseCircleOutline, IoIosInformationCircleOutline} from "react-icons/io";
import api from "../api/axiosConfig.js";
import {useAuth} from "../auth/useAuth.js";

// 연속 실패 시 재연결을 멈추는 횟수 (지수 백오프, 최대 30초 간격)
const MAX_RECONNECT_ATTEMPTS = 8;
const MAX_RECONNECT_DELAY_MS = 30000;

// 토스트 타입 정의
const TOAST_TYPES = {
    default: {
        className: 'toast-default',
        showIcon: true,
        icon: IoIosInformationCircleOutline
    },
    error: {
        className: 'toast-error',
        showIcon: true,
        icon: IoIosCloseCircleOutline
    }
};

// SSE 연결 상태
const SSE_STATUS = {
    DISCONNECTED: 'disconnected',
    CONNECTING: 'connecting',
    CONNECTED: 'connected',
    ERROR: 'error'
};

// Toast Context 생성
const ToastContext = createContext();

// Toast Provider 컴포넌트 (SSE 기능 통합)
export const ToastProvider = ({
                                  children,
                                  autoReconnect = true, // 자동 재연결 여부
                                  reconnectInterval = 3000, // 재연결 간격 (ms)
                                  enableSSEToasts = true // SSE 메시지를 자동으로 토스트로 표시할지 여부
                              }) => {
    const {isLoggedIn} = useAuth();
    const [toasts, setToasts] = useState([]);
    const [sseStatus, setSseStatus] = useState(SSE_STATUS.DISCONNECTED);


    const eventSourceRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const reconnectAttemptsRef = useRef(0);
    const connectRef = useRef(() => {});
    const sseEnabledRef = useRef(false); // 로그아웃 뒤 진행 중이던 재연결이 다시 붙지 않도록

    const [userCash, setUserCash] = useState(0); // 사용자 현금 상태
    const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0); // 읽지 않은 알림 개수

    const sseUrl = `${import.meta.env.VITE_API_URL}/notifications/subscribe`;

    // 토스트 추가 함수
    const addToast = useCallback((message, type = 'default', duration = 5000) => {
        const id = Date.now() + Math.random();
        const newToast = {
            id,
            message,
            type,
            duration,
            isLeaving: false
        };

        setToasts(prev => [...prev, newToast]);

        // 지정된 시간 후 사라지는 애니메이션 시작
        if (duration > 0) {
            setTimeout(() => {
                setToasts(prev => prev.map(toast =>
                    toast.id === id ? {...toast, isLeaving: true} : toast
                ));

                // 애니메이션 후 실제 제거 (300ms 후)
                setTimeout(() => {
                    removeToast(id);
                }, 300);
            }, duration);
        }

        return id;
    }, []);

    // 토스트 제거 함수
    const removeToast = useCallback((id) => {
        setToasts(prev => prev.filter(toast => toast.id !== id));
    }, []);

    // 읽지 않은 알림 개수 가져오는 함수
    const fetchUnreadNotificationsCount = useCallback(async () => {
        try {
            const response = await api.get('/notifications/count');
            setUnreadNotificationsCount(response.data);
        } catch (error) {
            console.error('읽지 않은 알림 개수 가져오기 실패:', error);
        }
    }, []);

    // SSE 메시지 처리 함수
    const handleSSEMessage = useCallback((event) => {
        try {
            // JSON 파싱 시도
            const notificationData = JSON.parse(event.data);

            if (enableSSEToasts && notificationData.message) {
                // 1. 토스트 메시지 타입 결정
                const toastType = notificationData.messageType === 'ERROR' ? 'error' : 'default';

                // 2. 실제 토스트에 표시할 메시지 구성
                let displayMessage = "";

                switch (notificationData.message) {
                    case 'ORDER_EXECUTION': {
                        const {orderType, symbol, amount, tradePrice, cash} = notificationData.data;
                        const cutSymbol = symbol.split('-')[1];
                        displayMessage += `예약 ${orderType === 'BUY' ? '매수' : '매도'} 주문이 체결되었습니다.\n`;
                        displayMessage += `수량 : ${parseFloat(amount).toFixed(7)} ${cutSymbol}\n`;
                        displayMessage += `체결 : 개당 ${tradePrice.toLocaleString()}원`;
                        setUserCash(cash); // 사용자 현금 상태 업데이트
                        break;
                    }
                    case 'SEASON_UPDATE': {
                        const {seasonName, seasonFlag} = notificationData.data;
                        if (seasonFlag === 'season') {
                            displayMessage += `🎉 ${seasonName} 스플릿 1 🎉\n`;
                            displayMessage += `새로운 시즌이 시작되었습니다!\n`;
                            displayMessage += `이전 랭킹과 수익률을 확인해보세요.`;
                        } else if (seasonFlag === 'split') {
                            displayMessage += `🚀 ${seasonName} 스플릿 2 🚀\n`;
                            displayMessage += `새로운 스플릿이 시작되었습니다!\n`;
                            displayMessage += `10,000,000원의 추가 자금이 지급됩니다.`;
                        }
                        break;
                    }
                    case 'NOTICE': {
                        const {title} = notificationData.data;
                        displayMessage += `🔔 새로운 공지사항이 등록되었습니다.\n`;
                        displayMessage += `공지사항 탭에서 확인해보세요.\n`;
                        displayMessage += `[${title}]\n`;
                        break;
                    }
                }

                // 3. 토스트 추가
                addToast(displayMessage, toastType);

                // 4. db에서 안읽은 알림 개수 업데이트
                fetchUnreadNotificationsCount();
            }

        } catch (e) {
            console.error('SSE 메시지 파싱 오류:', e);
        }
    }, [addToast, enableSSEToasts, fetchUnreadNotificationsCount]);

    // SSE 연결 함수
    const connectSSE = useCallback(() => {
        if (!sseUrl || eventSourceRef.current) return;

        setSseStatus(SSE_STATUS.CONNECTING);

        try {
            const eventSource = new EventSource(sseUrl, {withCredentials: true});
            eventSourceRef.current = eventSource;

            eventSource.onopen = () => {
                setSseStatus(SSE_STATUS.CONNECTED);
                reconnectAttemptsRef.current = 0;
            };

            eventSource.onmessage = handleSSEMessage;

            eventSource.onerror = () => {
                setSseStatus(SSE_STATUS.ERROR);

                // 브라우저 기본 재연결은 401이어도 계속 시도하므로 직접 닫고 다시 연결한다
                eventSource.close();
                eventSourceRef.current = null;

                if (!autoReconnect) return;
                const attempt = ++reconnectAttemptsRef.current;
                if (attempt > MAX_RECONNECT_ATTEMPTS) {
                    console.warn('SSE 재연결을 중단합니다.');
                    return;
                }
                const delay = Math.min(reconnectInterval * 2 ** (attempt - 1), MAX_RECONNECT_DELAY_MS);
                reconnectTimeoutRef.current = setTimeout(async () => {
                    // EventSource는 응답 상태 코드를 알려주지 않으므로 axios 요청으로 인증을 먼저 확인한다.
                    // 액세스 토큰이 만료됐으면 인터셉터가 재발급하고, 재발급도 실패하면 로그아웃(forceLogout)되어
                    // isLoggedIn이 false가 되면서 연결이 정리된다.
                    try {
                        await api.get('/notifications/count');
                        if (sseEnabledRef.current) connectRef.current();
                    } catch {
                        // 인증 실패: 로그아웃 처리에 맡기고 재연결하지 않음
                    }
                }, delay);
            };

        } catch (error) {
            console.error('SSE 연결 실패:', error);
            setSseStatus(SSE_STATUS.ERROR);
        }
    }, [sseUrl, autoReconnect, reconnectInterval, handleSSEMessage]);
    connectRef.current = connectSSE;

    // SSE 연결 해제 함수
    const disconnectSSE = useCallback(() => {
        if (reconnectTimeoutRef.current) {
            clearTimeout(reconnectTimeoutRef.current);
            reconnectTimeoutRef.current = null;
        }

        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }

        setSseStatus(SSE_STATUS.DISCONNECTED);
        reconnectAttemptsRef.current = 0;
    }, []);

    // 로그인한 동안에만 SSE 연결 (비로그인 상태에서 401 재연결이 반복되지 않도록)
    useEffect(() => {
        if (!isLoggedIn) {
            setUnreadNotificationsCount(0);
            return;
        }

        sseEnabledRef.current = true;
        connectSSE();
        fetchUnreadNotificationsCount();

        return () => {
            sseEnabledRef.current = false;
            disconnectSSE();
        };
    }, [isLoggedIn, connectSSE, disconnectSSE, fetchUnreadNotificationsCount]);

    const value = {
        toasts,
        addToast,
        removeToast, // 내부용으로만 사용
        userCash, // 사용자 현금 상태
        unreadNotificationsCount, // 읽지 않은 알림 개수
        setUnreadNotificationsCount, // 알림 개수 업데이트 함수

        // SSE 관련
        sseStatus,
        connectSSE,
        disconnectSSE,
        isSSEConnected: sseStatus === SSE_STATUS.CONNECTED
    };

    return (
        <ToastContext.Provider value={value}>
            {children}
            <ToastContainer/>
        </ToastContext.Provider>
    );
};

// 개별 Toast 컴포넌트
const Toast = ({toast}) => {
    const [isVisible, setIsVisible] = useState(false);

    const toastConfig = TOAST_TYPES[toast.type] || TOAST_TYPES.default;
    const IconComponent = toastConfig.icon;

    useEffect(() => {
        // 마운트 시 애니메이션 시작
        setTimeout(() => setIsVisible(true), 10);
    }, []);

    const toastClasses = `
    toast-base 
    ${toastConfig.className}
    ${isVisible && !toast.isLeaving ? 'toast-visible' : 'toast-hidden'}
  `;

    return (
        <div className={toastClasses}>
            {toastConfig.showIcon && IconComponent && (
                <IconComponent size={20} className="toast-icon"/>
            )}

            <span className="toast-message">
        {toast.message}
      </span>
        </div>
    );
};

// Toast Container 컴포넌트
const ToastContainer = () => {
    const {toasts, removeToast} = useContext(ToastContext);

    return (
        <div className="toast-container">
            {toasts.map((toast) => (
                <Toast
                    key={toast.id}
                    toast={toast}
                    onRemove={removeToast}
                />
            ))}
        </div>
    );
};

// useToast 훅
export const useToast = () => {
    const context = useContext(ToastContext);

    if (!context) {
        throw new Error('useToast는 ToastProvider 내부에서 사용되어야 합니다.');
    }

    const {addToast, userCash, unreadNotificationsCount, setUnreadNotificationsCount} = context;

    return {
        // 편의 메서드들
        infoAlert: (message, duration) => addToast(message, 'default', duration),
        errorAlert: (message, duration) => addToast(message, 'error', duration),

        // 기본 메서드
        addToast,
        // 유저 현금 상태
        userCash,
        unreadNotificationsCount, // 읽지 않은 알림 개수
        setUnreadNotificationsCount, // 알림 개수 업데이트 함수
    };
};

