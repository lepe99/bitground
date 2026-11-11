import {lazy, Suspense, useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useNotifications} from '../notifications/useNotifications.js';
import {useMediaQuery} from '../hooks/useMediaQuery.js';

// 목록은 버튼을 눌렀을 때 받는다
const NotificationPanel = lazy(() => import('./NotificationPanel.jsx'));
const loadingPanel = <p className="py-10 text-center text-sm text-fg-3">불러오는 중…</p>;

/** 모바일: 아래에서 올라오는 시트 (Esc·바깥을 누르면 닫힘) */
function MobileSheet({onClose, children}) {
    const ref = useRef(null);
    useEffect(() => {
        ref.current?.showModal();
    }, []);
    return (
        <dialog
            ref={ref}
            aria-label="알림"
            onClose={onClose}
            onClick={(e) => {
                if (e.target === ref.current) onClose();
            }}
            className="fixed inset-x-0 top-auto bottom-0 m-0 flex h-[80dvh] max-h-none w-full max-w-none flex-col rounded-t-3xl border-0 bg-surface p-0 pb-[env(safe-area-inset-bottom)] text-fg backdrop:bg-black/50"
        >
            <div className="mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-line" aria-hidden="true"/>
            <div className="flex shrink-0 items-center justify-between px-5 pt-2 pb-2">
                <span className="text-base font-bold">알림</span>
                <button type="button" onClick={onClose} aria-label="닫기" className="flex size-9 items-center justify-center rounded-full text-fg-2 hover:bg-surface-2">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
                </button>
            </div>
            {children}
        </dialog>
    );
}

/**
 * 헤더의 알림 버튼 + 알림 목록 (PC는 드롭다운, 모바일은 아래에서 올라오는 시트).
 * 열기만 해서는 읽음이 되지 않고, 누른 알림만 읽음이 된다 ('모두 읽음'은 따로)
 */
export default function NotificationMenu() {
    const navigate = useNavigate();
    const desktop = useMediaQuery('(min-width: 768px)');
    const {unreadNotificationsCount} = useNotifications();
    const [open, setOpen] = useState(false);
    const rootRef = useRef(null);

    // PC 드롭다운: 바깥을 누르거나 Esc를 누르면 닫는다 (모바일 시트는 dialog가 처리)
    useEffect(() => {
        if (!open || !desktop) return;
        const onPointer = (e) => {
            if (!rootRef.current?.contains(e.target)) setOpen(false);
        };
        const onKey = (e) => {
            if (e.key === 'Escape') setOpen(false);
        };
        document.addEventListener('mousedown', onPointer);
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('mousedown', onPointer);
            document.removeEventListener('keydown', onKey);
        };
    }, [open, desktop]);

    const go = (to) => {
        setOpen(false);
        if (to) navigate(to);
    };

    const unread = unreadNotificationsCount > 99 ? '99+' : unreadNotificationsCount;

    // 읽지 않은 개수가 늘면 배지를 한 번 튀게 한다 (key가 바뀌면 애니메이션을 다시 시작)
    const previousCount = useRef(unreadNotificationsCount);
    const [bump, setBump] = useState(0);
    useEffect(() => {
        if (unreadNotificationsCount > previousCount.current) setBump((b) => b + 1);
        previousCount.current = unreadNotificationsCount;
    }, [unreadNotificationsCount]);

    return (
        <div ref={rootRef} className="relative">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                aria-expanded={open}
                aria-haspopup="dialog"
                aria-label={unreadNotificationsCount > 0 ? `알림 ${unreadNotificationsCount}개` : '알림'}
                className="relative flex size-10 items-center justify-center rounded-[10px] text-fg-2 hover:bg-surface-2 hover:text-fg md:border md:border-line"
            >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/>
                    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>
                </svg>
                {unreadNotificationsCount > 0 && (
                    <span
                        key={bump}
                        className={`num absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-up px-1 text-[10px] font-bold text-white ${
                            bump > 0 ? 'motion-safe:animate-[badge-bump_450ms_ease-out]' : ''
                        }`}
                    >
                        {unread}
                    </span>
                )}
            </button>

            {open && desktop && (
                <div className="absolute right-0 top-12 z-50 flex w-[380px] flex-col overflow-hidden rounded-2xl border border-line bg-surface pt-3 shadow-xl">
                    <div className="px-4 pb-2 text-sm font-bold">알림</div>
                    <Suspense fallback={loadingPanel}><NotificationPanel onNavigate={go} listClassName="max-h-[460px]"/></Suspense>
                </div>
            )}
            {open && !desktop && (
                <MobileSheet onClose={() => setOpen(false)}>
                    <Suspense fallback={loadingPanel}><NotificationPanel onNavigate={go} listClassName="flex-1"/></Suspense>
                </MobileSheet>
            )}
        </div>
    );
}
