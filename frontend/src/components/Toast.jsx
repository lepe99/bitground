import {useState, useCallback, useEffect, useMemo} from 'react';
import {ToastContext} from './toastContext.js';
import {kindOf} from '../notifications/notificationKinds.js';
import KindIcon from '../notifications/KindIcon.jsx';
import {summarize} from '../notifications/groupNotifications.js';

const INFO_ICON = 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-4M12 8h.01';
const ERROR_ICON = 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM15 9l-6 6M9 9l6 6';

// 사라지는 애니메이션 시간 (CSS transition과 맞춤)
const LEAVE_ANIMATION_MS = 300;
// 실시간 알림 토스트가 떠 있는 시간, 한 번에 보이는 최대 개수 (넘으면 오래된 것부터 닫음)
const NOTIFICATION_DURATION_MS = 6000;
const MAX_VISIBLE = 4;


// 화면 위쪽 알림 메시지. 실시간 알림(SSE)은 notifications/NotificationProvider가 showNotification으로 띄운다
// 보이는 토스트가 너무 많으면 가장 오래된 것부터 닫는다
function withLimit(toasts) {
    const live = toasts.filter(t => !t.isLeaving);
    if (live.length <= MAX_VISIBLE) return toasts;
    const overflow = new Set(live.slice(0, live.length - MAX_VISIBLE).map(t => t.id));
    return toasts.map(t => overflow.has(t.id) ? {...t, isLeaving: true} : t);
}

export const ToastProvider = ({children}) => {
    const [toasts, setToasts] = useState([]);

    // 닫기: 사라지는 애니메이션을 시작한다 (끝나면 토스트가 remove를 부른다)
    const dismiss = useCallback((id) => {
        setToasts(prev => prev.map(toast => toast.id === id ? {...toast, isLeaving: true} : toast));
    }, []);
    const remove = useCallback((id) => {
        setToasts(prev => prev.filter(toast => toast.id !== id));
    }, []);

    const addToast = useCallback((message, type = 'default', duration = 5000) => {
        const id = Date.now() + Math.random();
        setToasts(prev => withLimit([...prev, {id, message, type, duration, version: 0, isLeaving: false}]));
        return id;
    }, []);

    /**
     * 실시간 알림 토스트. 같은 묶음(key)의 토스트가 떠 있으면 새로 띄우지 않고 합쳐서 개수를 늘린다
     * (같은 글에 댓글이 연달아 달리거나 예약 주문이 한꺼번에 체결될 때).
     * item: {id, messageType, message, postId}, onOpen(items): 토스트를 눌렀을 때
     */
    const showNotification = useCallback(({key, item, onOpen}) => {
        setToasts(prev => {
            const index = key ? prev.findIndex(t => t.key === key && !t.isLeaving) : -1;
            if (index >= 0) {
                const target = prev[index];
                const updated = {...target, items: [item, ...target.items], onOpen, version: target.version + 1};
                return prev.map((t, i) => i === index ? updated : t);
            }
            return withLimit([...prev, {
                id: Date.now() + Math.random(), type: 'notification', key, items: [item], onOpen,
                duration: NOTIFICATION_DURATION_MS, version: 0, isLeaving: false,
            }]);
        });
    }, []);

    // 알림 함수는 토스트 목록이 바뀌어도 같은 참조를 유지한다.
    // (목록에 묶어 두면 useEffect 의존성으로 쓴 곳이 토스트를 띄울 때마다 다시 실행돼 같은 알림이 반복된다)
    const infoAlert = useCallback((message, duration) => addToast(message, 'default', duration), [addToast]);
    const errorAlert = useCallback((message, duration) => addToast(message, 'error', duration), [addToast]);
    const value = useMemo(
        () => ({toasts, addToast, infoAlert, errorAlert, showNotification}),
        [toasts, addToast, infoAlert, errorAlert, showNotification]
    );

    return (
        <ToastContext.Provider value={value}>
            {children}
            <ToastContainer toasts={toasts} onDismiss={dismiss} onRemove={remove}/>
        </ToastContext.Provider>
    );
};

/**
 * 정해진 시간이 지나면 닫는다. 마우스를 올려 둔 동안은 멈추고, 내용이 합쳐지면(version) 처음부터 다시 센다.
 * 닫히기 시작하면 애니메이션이 끝난 뒤 목록에서 지운다
 */
function useLifecycle(toast, {onDismiss, onRemove}, paused) {
    useEffect(() => {
        if (paused || toast.isLeaving || !(toast.duration > 0)) return undefined;
        const timer = setTimeout(() => onDismiss(toast.id), toast.duration);
        return () => clearTimeout(timer);
    }, [toast.id, toast.duration, toast.version, toast.isLeaving, paused, onDismiss]);
    useEffect(() => {
        if (!toast.isLeaving) return undefined;
        const timer = setTimeout(() => onRemove(toast.id), LEAVE_ANIMATION_MS);
        return () => clearTimeout(timer);
    }, [toast.id, toast.isLeaving, onRemove]);
}

function useEnter() {
    const [isVisible, setIsVisible] = useState(false);
    useEffect(() => {
        // 마운트 직후 한 프레임 뒤에 나타나게 해서 슬라이드 효과를 준다
        const timer = setTimeout(() => setIsVisible(true), 10);
        return () => clearTimeout(timer);
    }, []);
    return isVisible;
}

const motion = (shown) => `transition duration-300 ${shown ? 'translate-y-0 opacity-100' : '-translate-y-2 opacity-0'}`;

// 일반 메시지 토스트 (저장·오류 안내 등)
const Toast = ({toast, onDismiss, onRemove}) => {
    const isVisible = useEnter();
    useLifecycle(toast, {onDismiss, onRemove}, false);
    const error = toast.type === 'error';
    return (
        <div
            role={error ? 'alert' : 'status'}
            className={`pointer-events-auto flex items-center gap-3 rounded-2xl px-4 py-3.5 shadow-xl max-w-[360px] ${
                error ? 'bg-up text-white' : 'border border-line bg-surface text-fg'
            } ${motion(isVisible && !toast.isLeaving)}`}
        >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={`shrink-0 ${error ? '' : 'text-brand-fg'}`} aria-hidden="true">
                <path d={error ? ERROR_ICON : INFO_ICON}/>
            </svg>
            <span className="flex-1 whitespace-pre-wrap text-sm font-medium">{toast.message}</span>
        </div>
    );
};

// 실시간 알림 토스트: 종류 아이콘, 합친 개수, 누르면 해당 화면으로 이동, 닫기 버튼
const NotificationToast = ({toast, onDismiss, onRemove}) => {
    const isVisible = useEnter();
    const [hovered, setHovered] = useState(false);
    useLifecycle(toast, {onDismiss, onRemove}, hovered);

    const {items} = toast;
    const latest = items[0];
    const kind = kindOf({messageType: latest.messageType, message: items.map(i => i.message).join('\n')});
    const {title, body} = summarize(items);

    return (
        <div
            role="status"
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
            className={`pointer-events-auto relative flex w-full max-w-[360px] rounded-2xl border border-line bg-surface text-fg shadow-xl md:w-[360px] ${motion(isVisible && !toast.isLeaving)}`}
        >
            <button
                type="button"
                onClick={() => {
                    toast.onOpen?.(items);
                    onDismiss(toast.id);
                }}
                className="flex min-w-0 flex-1 gap-3 rounded-2xl py-3 pr-2 pl-3.5 text-left hover:bg-surface-2"
            >
                <span className="relative mt-0.5">
                    <KindIcon kind={kind}/>
                    {items.length > 1 && (
                        <span key={items.length} className="num absolute -right-1.5 -bottom-1 rounded-full border-2 border-surface bg-fg px-1 text-[10px] leading-[14px] font-bold text-bg motion-safe:animate-[badge-bump_450ms_ease-out]">
                            {items.length}
                        </span>
                    )}
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="text-sm leading-snug font-bold">{title}</span>
                    {body && <span className="line-clamp-2 whitespace-pre-line text-[13px] leading-relaxed text-fg-3">{body}</span>}
                </span>
            </button>
            <button
                type="button"
                onClick={() => onDismiss(toast.id)}
                aria-label="알림 닫기"
                className="m-1.5 flex size-7 shrink-0 items-center justify-center rounded-full text-fg-3 hover:bg-surface-2 hover:text-fg"
            >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
            </button>
        </div>
    );
};

const ToastContainer = ({toasts, onDismiss, onRemove}) => (
    <div className="pointer-events-none fixed inset-x-3 top-16 z-[10000] flex flex-col items-end gap-2 md:left-auto md:right-5 md:top-20" aria-live="polite">
        {toasts.map((toast) => toast.type === 'notification'
            ? <NotificationToast key={toast.id} toast={toast} onDismiss={onDismiss} onRemove={onRemove}/>
            : <Toast key={toast.id} toast={toast} onDismiss={onDismiss} onRemove={onRemove}/>)}
    </div>
);
