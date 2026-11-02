import React, {createContext, useContext, useState, useCallback, useEffect, useMemo} from 'react';
import {IoIosCloseCircleOutline, IoIosInformationCircleOutline} from "react-icons/io";

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

// 사라지는 애니메이션 시간 (CSS transition과 맞춤)
const LEAVE_ANIMATION_MS = 300;

const ToastContext = createContext(null);

// 화면 하단 알림 메시지. 실시간 알림(SSE)은 notifications/NotificationProvider가 이 토스트를 사용한다
export const ToastProvider = ({children}) => {
    const [toasts, setToasts] = useState([]);

    const removeToast = useCallback((id) => {
        setToasts(prev => prev.filter(toast => toast.id !== id));
    }, []);

    const addToast = useCallback((message, type = 'default', duration = 5000) => {
        const id = Date.now() + Math.random();
        setToasts(prev => [...prev, {id, message, type, duration, isLeaving: false}]);

        // 지정된 시간 후 사라지는 애니메이션을 시작하고, 애니메이션이 끝나면 제거
        if (duration > 0) {
            setTimeout(() => {
                setToasts(prev => prev.map(toast => toast.id === id ? {...toast, isLeaving: true} : toast));
                setTimeout(() => removeToast(id), LEAVE_ANIMATION_MS);
            }, duration);
        }
        return id;
    }, [removeToast]);

    // 함수 참조가 렌더마다 바뀌지 않도록 묶어 둔다 (useEffect 의존성으로 써도 반복 실행되지 않음)
    const value = useMemo(() => ({
        toasts,
        addToast,
        infoAlert: (message, duration) => addToast(message, 'default', duration),
        errorAlert: (message, duration) => addToast(message, 'error', duration),
    }), [toasts, addToast]);

    return (
        <ToastContext.Provider value={value}>
            {children}
            <ToastContainer toasts={toasts}/>
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
        const timer = setTimeout(() => setIsVisible(true), 10);
        return () => clearTimeout(timer);
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
            <span className="toast-message">{toast.message}</span>
        </div>
    );
};

const ToastContainer = ({toasts}) => (
    <div className="toast-container">
        {toasts.map((toast) => <Toast key={toast.id} toast={toast}/>)}
    </div>
);

// useToast 훅: { infoAlert, errorAlert, addToast }
export const useToast = () => {
    const context = useContext(ToastContext);
    if (!context) {
        throw new Error('useToast는 ToastProvider 내부에서 사용되어야 합니다.');
    }
    const {infoAlert, errorAlert, addToast} = context;
    return {infoAlert, errorAlert, addToast};
};
