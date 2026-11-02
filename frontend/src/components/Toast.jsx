import React, {createContext, useContext, useState, useCallback, useEffect, useRef} from 'react';
import {IoIosCloseCircleOutline, IoIosInformationCircleOutline} from "react-icons/io";
import api from "../api/axiosConfig.js";

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
    const [toasts, setToasts] = useState([]);
    const [sseStatus, setSseStatus] = useState(SSE_STATUS.DISCONNECTED);


    const eventSourceRef = useRef(null);
    const reconnectTimeoutRef = useRef(null);
    const reconnectAttemptsRef = useRef(0);

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
    const fetchUnreadNotificationsCount = async () => {
        try {
            const response = await api.get('/notifications/count');
            setUnreadNotificationsCount(response.data);
        } catch (error) {
            console.error('읽지 않은 알림 개수 가져오기 실패:', error);
        }
    }

    // SSE 메시지 처리 함수
    const handleSSEMessage = useCallback((event) => {
        try {
            // JSON 파싱 시도
            const notificationData = JSON.parse(event.data);
            console.log('Received SSE Notification:', notificationData);

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
    }, [addToast, enableSSEToasts]);

    // SSE 연결 함수
    const connectSSE = useCallback(() => {
        if (!sseUrl || eventSourceRef.current) return;

        setSseStatus(SSE_STATUS.CONNECTING);

        try {
            const eventSource = new EventSource(sseUrl, {withCredentials: true});
            eventSourceRef.current = eventSource;

            eventSource.onopen = () => {
                console.log('SSE 연결 성공');
                setSseStatus(SSE_STATUS.CONNECTED);
                reconnectAttemptsRef.current = 0;
            };

            eventSource.onmessage = handleSSEMessage;

            eventSource.onerror = (error) => {
                console.error('SSE 연결 오류:', error);
                setSseStatus(SSE_STATUS.ERROR);

                // 연결 종료
                eventSource.close();
                eventSourceRef.current = null;

                // 자동 재연결
                if (autoReconnect) {
                    reconnectAttemptsRef.current++;
                    const delay = Math.min(reconnectInterval * reconnectAttemptsRef.current, 30000); // 최대 30초

                    console.log(`${delay / 1000}초 후 SSE 재연결 시도 (${reconnectAttemptsRef.current}번째)`);

                    reconnectTimeoutRef.current = setTimeout(() => {
                        connectSSE();
                    }, delay);
                }
            };

        } catch (error) {
            console.error('SSE 연결 실패:', error);
            setSseStatus(SSE_STATUS.ERROR);
        }
    }, [sseUrl, autoReconnect, reconnectInterval, handleSSEMessage]);

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

    // 컴포넌트 마운트 시 SSE 연결
    useEffect(() => {
        if (sseUrl) {
            connectSSE();
        }

        // 읽지 않은 알림 개수 초기화
        fetchUnreadNotificationsCount();

        // 클린업
        return () => {
            disconnectSSE();
        };
    }, [sseUrl, connectSSE, disconnectSSE]);

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

