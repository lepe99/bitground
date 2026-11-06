import {useCallback, useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useNotifications} from '../notifications/useNotifications.js';
import {fetchNotifications, markAllNotificationsRead} from '../api/notificationApi.js';

const PAGE_SIZE = 10;

// 알림 종류별 이동할 화면 (댓글 알림은 그 게시글)
const TARGETS = {
    NOTICE: '/notice',
    ORDER_EXECUTION: '/investments',
    SEASON_UPDATE: '/rank',
};
const targetOf = (item) => ((item.messageType === 'COMMENT' || item.messageType === 'INQUIRY') && item.postId
    ? `/community/${item.postId}` : TARGETS[item.messageType]);

/** 헤더의 알림 버튼 + 펼침 목록. 열면 모두 읽음 처리하고, 끝까지 내리면 다음 쪽을 불러온다 */
export default function NotificationMenu() {
    const navigate = useNavigate();
    const {unreadNotificationsCount, setUnreadNotificationsCount} = useNotifications();
    const [open, setOpen] = useState(false);
    const [items, setItems] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(false);
    const rootRef = useRef(null);

    // 바깥을 누르거나 Esc를 누르면 닫는다
    useEffect(() => {
        if (!open) return;
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
    }, [open]);

    const loadPage = useCallback(async (target) => {
        setLoading(true);
        try {
            const result = await fetchNotifications({page: target, size: PAGE_SIZE});
            const content = result.content ?? result;
            setItems((prev) => (target === 0 ? content : [...prev, ...content]));
            setTotalPages(result.totalPages ?? 1);
            setPage(target);
        } catch (error) {
            console.error('알림 목록을 불러오지 못했습니다:', error);
        } finally {
            setLoading(false);
        }
    }, []);

    const toggle = () => {
        const next = !open;
        setOpen(next);
        if (!next) return;
        void loadPage(0);
        if (unreadNotificationsCount > 0) {
            markAllNotificationsRead()
                .then(() => setUnreadNotificationsCount(0))
                .catch((error) => console.error('알림 읽음 처리 실패:', error));
        }
    };

    const onScroll = (e) => {
        const {scrollTop, scrollHeight, clientHeight} = e.currentTarget;
        if (!loading && page < totalPages - 1 && (scrollTop + clientHeight) / scrollHeight > 0.9) {
            void loadPage(page + 1);
        }
    };

    const go = (item) => {
        const to = targetOf(item);
        if (to) navigate(to);
        setOpen(false);
    };

    const unread = unreadNotificationsCount > 99 ? '99+' : unreadNotificationsCount;

    return (
        <div ref={rootRef} className="relative">
            <button
                type="button"
                onClick={toggle}
                aria-expanded={open}
                aria-haspopup="true"
                aria-label={unreadNotificationsCount > 0 ? `알림 ${unreadNotificationsCount}개` : '알림'}
                className="relative flex size-10 items-center justify-center rounded-[10px] text-fg-2 hover:bg-surface-2 hover:text-fg md:border md:border-line"
            >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/>
                    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>
                </svg>
                {unreadNotificationsCount > 0 && (
                    <span className="num absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-up px-1 text-[10px] font-bold text-white">
                        {unread}
                    </span>
                )}
            </button>

            {open && (
                <div className="absolute right-0 top-12 z-50 w-[min(340px,calc(100vw-24px))] overflow-hidden rounded-2xl border border-line bg-surface shadow-xl">
                    <div className="border-b border-line px-4 py-3 text-sm font-bold">알림</div>
                    <ul className="max-h-[360px] overflow-y-auto" onScroll={onScroll}>
                        {items.length === 0 && !loading && (
                            <li className="px-4 py-8 text-center text-sm text-fg-3">새로운 알림이 없습니다.</li>
                        )}
                        {items.map((item, index) => (
                            <li key={`${item.id}-${index}`} className="border-b border-line last:border-b-0">
                                <button type="button" onClick={() => go(item)} className="flex w-full flex-col gap-1 px-4 py-3 text-left hover:bg-surface-2">
                                    <span className="whitespace-pre-wrap text-sm leading-relaxed">{item.message}</span>
                                    {item.createdAt && (
                                        <span className="num text-xs text-fg-3">{new Date(item.createdAt).toLocaleString('ko-KR')}</span>
                                    )}
                                </button>
                            </li>
                        ))}
                        {loading && <li className="px-4 py-3 text-center text-xs text-fg-3">불러오는 중…</li>}
                    </ul>
                </div>
            )}
        </div>
    );
}
