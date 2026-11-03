import {useState, useCallback, useEffect, useMemo} from 'react';
import {ToastContext} from './toastContext.js';

const INFO_ICON = 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-4M12 8h.01';
const ERROR_ICON = 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM15 9l-6 6M9 9l6 6';

// 사라지는 애니메이션 시간 (CSS transition과 맞춤)
const LEAVE_ANIMATION_MS = 300;


// 화면 위쪽 알림 메시지. 실시간 알림(SSE)은 notifications/NotificationProvider가 이 토스트를 사용한다
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

    // 알림 함수는 토스트 목록이 바뀌어도 같은 참조를 유지한다.
    // (목록에 묶어 두면 useEffect 의존성으로 쓴 곳이 토스트를 띄울 때마다 다시 실행돼 같은 알림이 반복된다)
    const infoAlert = useCallback((message, duration) => addToast(message, 'default', duration), [addToast]);
    const errorAlert = useCallback((message, duration) => addToast(message, 'error', duration), [addToast]);
    const value = useMemo(() => ({toasts, addToast, infoAlert, errorAlert}), [toasts, addToast, infoAlert, errorAlert]);

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
    const error = toast.type === 'error';

    useEffect(() => {
        // 마운트 직후 한 프레임 뒤에 나타나게 해서 슬라이드 효과를 준다
        const timer = setTimeout(() => setIsVisible(true), 10);
        return () => clearTimeout(timer);
    }, []);

    const shown = isVisible && !toast.isLeaving;
    return (
        <div
            role={error ? 'alert' : 'status'}
            className={`pointer-events-auto flex w-full items-center gap-3 rounded-2xl px-4 py-3.5 shadow-xl transition duration-300 md:w-[360px] ${
                error ? 'bg-up text-white' : 'border border-line bg-surface text-fg'
            } ${shown ? 'translate-y-0 opacity-100' : '-translate-y-2 opacity-0'}`}
        >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={`shrink-0 ${error ? '' : 'text-brand-fg'}`} aria-hidden="true">
                <path d={error ? ERROR_ICON : INFO_ICON}/>
            </svg>
            <span className="flex-1 whitespace-pre-wrap text-sm font-medium">{toast.message}</span>
        </div>
    );
};

const ToastContainer = ({toasts}) => (
    <div className="pointer-events-none fixed inset-x-3 top-16 z-[10000] flex flex-col items-center gap-2 md:inset-x-auto md:top-20 md:right-5 md:items-end">
        {toasts.map((toast) => <Toast key={toast.id} toast={toast}/>)}
    </div>
);
