import {useCallback, useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useNotifications} from '../notifications/useNotifications.js';
import {fetchNotifications, markAllNotificationsRead, markNotificationRead} from '../api/notificationApi.js';
import {useMediaQuery} from '../hooks/useMediaQuery.js';

const PAGE_SIZE = 15;

// 목록 탭 (서버의 NotificationGroup)
const GROUPS = [
    {value: 'ALL', label: '전체'},
    {value: 'TRADE', label: '거래'},
    {value: 'COMMUNITY', label: '커뮤니티'},
    {value: 'SYSTEM', label: '공지·시즌'},
];

// 알림 종류별 이동할 화면 (댓글·문의 알림은 그 게시글)
const TARGETS = {
    NOTICE: '/notice',
    ORDER_EXECUTION: '/investments',
    SEASON_UPDATE: '/rank',
};
const targetOf = (item) => ((item.messageType === 'COMMENT' || item.messageType === 'INQUIRY') && item.postId
    ? `/community/${item.postId}` : TARGETS[item.messageType]);

const ICON_PATHS = {
    trade: <><path d="M7 17L17 7"/><path d="M9 7h8v8"/></>,
    comment: <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>,
    inquiry: <><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.5V14"/><path d="M12 17.2v.1"/></>,
    notice: <><path d="M4 10v4h3l6 4V6L7 10H4z"/><path d="M17 9a4 4 0 0 1 0 6"/></>,
    season: <><path d="M8 4h8v5a4 4 0 0 1-8 0V4z"/><path d="M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4"/><path d="M12 13v4M9 20h6"/></>,
};

/** 알림 종류별 아이콘·색. 체결은 매수(빨강)·매도(파랑) */
function kindOf(item) {
    switch (item.messageType) {
        case 'ORDER_EXECUTION':
            return item.message?.includes('매도') ? {icon: 'trade', tone: 'bg-down/10 text-down'} : {icon: 'trade', tone: 'bg-up/10 text-up'};
        case 'COMMENT':
            return {icon: 'comment', tone: 'bg-brand-soft text-brand-fg'};
        case 'INQUIRY':
            return {icon: 'inquiry', tone: 'bg-brand-soft text-brand-fg'};
        case 'NOTICE':
            return {icon: 'notice', tone: 'bg-surface-2 text-fg'};
        default:
            return {icon: 'season', tone: 'bg-surface-2 text-fg'};
    }
}

// 날짜(문자열·밀리초·Date 모두)의 그날 0시
const startOfDay = (value) => {
    const d = new Date(value);
    return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
};
const DAY = 86_400_000;

/** 오늘·어제·이번 주(7일 안)·이전 */
function dayGroup(iso, now) {
    const diff = (startOfDay(now) - startOfDay(new Date(iso))) / DAY;
    if (diff <= 0) return '오늘';
    if (diff === 1) return '어제';
    if (diff < 7) return '이번 주';
    return '이전';
}

/** 방금 전 · 5분 전 · 3시간 전(오늘) · 어제 14:05 · 11월 2일 */
function relativeTime(iso, now) {
    const d = new Date(iso);
    const minutes = Math.floor((now - d) / 60_000);
    const group = dayGroup(iso, now);
    const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    if (group === '오늘') return `${Math.floor(minutes / 60)}시간 전`;
    if (group === '어제') return `어제 ${hm}`;
    return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** 저장된 문구의 첫 줄은 제목, 나머지는 본문 */
function splitMessage(message = '') {
    const [title, ...rest] = message.split('\n');
    return {title, body: rest.join('\n').trim()};
}

function NotificationItem({item, now, onOpen}) {
    const {icon, tone} = kindOf(item);
    const {title, body} = splitMessage(item.message);
    const unread = !item.isRead;
    return (
        <li>
            <button
                type="button"
                onClick={() => onOpen(item)}
                className={`flex w-full gap-3 px-4 py-3 text-left hover:bg-surface-2 ${unread ? 'bg-brand-soft/40' : ''}`}
            >
                <span className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full ${tone}`}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        {ICON_PATHS[icon]}
                    </svg>
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="flex items-start gap-2">
                        <span className={`min-w-0 flex-1 text-sm leading-snug ${unread ? 'font-bold' : 'font-semibold text-fg-2'}`}>{title}</span>
                        <span className="num shrink-0 pt-px text-[11px] text-fg-3">{relativeTime(item.createdAt, now)}</span>
                    </span>
                    {body && <span className="line-clamp-2 whitespace-pre-line text-[13px] leading-relaxed text-fg-3">{body}</span>}
                </span>
                <span className={`mt-2 size-2 shrink-0 rounded-full ${unread ? 'bg-up' : 'bg-transparent'}`} aria-label={unread ? '읽지 않음' : undefined}/>
            </button>
        </li>
    );
}

/** 알림 목록 내용 (PC 드롭다운·모바일 시트 공통): 탭 · 모두 읽음 · 날짜별 묶음 · 끝까지 내리면 다음 쪽 */
function NotificationPanel({onNavigate, listClassName}) {
    const {unreadNotificationsCount, setUnreadNotificationsCount} = useNotifications();
    const [group, setGroup] = useState('ALL');
    const [items, setItems] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [now] = useState(() => Date.now());

    const loadPage = useCallback(async (target, targetGroup) => {
        setLoading(true);
        try {
            const result = await fetchNotifications({page: target, size: PAGE_SIZE, group: targetGroup});
            const content = result.content ?? [];
            setItems((prev) => (target === 0 ? content : [...prev, ...content]));
            setTotalPages(result.totalPages ?? 1);
            setPage(target);
        } catch (error) {
            console.error('알림 목록을 불러오지 못했습니다:', error);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadPage(0, group);
    }, [group, loadPage]);

    const onScroll = (e) => {
        const {scrollTop, scrollHeight, clientHeight} = e.currentTarget;
        if (!loading && page < totalPages - 1 && (scrollTop + clientHeight) / scrollHeight > 0.9) {
            void loadPage(page + 1, group);
        }
    };

    // 누른 알림만 읽음으로 바꾸고 해당 화면으로 이동
    const open = (item) => {
        if (!item.isRead) {
            setItems((prev) => prev.map((n) => (n.id === item.id ? {...n, isRead: true} : n)));
            setUnreadNotificationsCount((count) => Math.max(0, count - 1));
            markNotificationRead(item.id).catch((error) => console.error('알림 읽음 처리 실패:', error));
        }
        onNavigate(targetOf(item));
    };

    const readAll = () => {
        setItems((prev) => prev.map((n) => ({...n, isRead: true})));
        setUnreadNotificationsCount(0);
        markAllNotificationsRead().catch((error) => console.error('알림 읽음 처리 실패:', error));
    };

    // 날짜별로 묶기 (목록은 최신순)
    const sections = [];
    for (const item of items) {
        const label = dayGroup(item.createdAt, now);
        if (sections.at(-1)?.label !== label) sections.push({label, items: []});
        sections.at(-1).items.push(item);
    }

    return (
        <div className="flex min-h-0 flex-1 flex-col">
            <div className="flex items-center justify-between gap-2 px-4 pb-2">
                <div role="tablist" aria-label="알림 종류" className="flex gap-1 overflow-x-auto">
                    {GROUPS.map((g) => (
                        <button
                            key={g.value}
                            type="button"
                            role="tab"
                            aria-selected={group === g.value}
                            onClick={() => setGroup(g.value)}
                            className={`h-8 shrink-0 rounded-full px-3 text-[13px] font-semibold ${
                                group === g.value ? 'bg-fg text-bg' : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
                            }`}
                        >
                            {g.label}
                        </button>
                    ))}
                </div>
                <button
                    type="button"
                    onClick={readAll}
                    disabled={unreadNotificationsCount === 0}
                    className="shrink-0 rounded-lg px-2 py-1 text-xs font-semibold text-fg-2 hover:bg-surface-2 hover:text-fg disabled:text-fg-3 disabled:hover:bg-transparent"
                >
                    모두 읽음
                </button>
            </div>
            <div className={`overflow-y-auto overscroll-contain border-t border-line ${listClassName}`} onScroll={onScroll}>
                {items.length === 0 && !loading && (
                    <p className="px-4 py-12 text-center text-sm text-fg-3">알림이 없어요.</p>
                )}
                {sections.map((section) => (
                    <section key={section.label} aria-label={section.label}>
                        <h3 className="sticky top-0 z-10 bg-surface px-4 pt-3 pb-1 text-xs font-bold text-fg-3">{section.label}</h3>
                        <ul>
                            {section.items.map((item) => <NotificationItem key={item.id} item={item} now={now} onOpen={open}/>)}
                        </ul>
                    </section>
                ))}
                {loading && <p className="px-4 py-3 text-center text-xs text-fg-3">불러오는 중…</p>}
            </div>
        </div>
    );
}

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
                    <span className="num absolute -right-1 -top-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-up px-1 text-[10px] font-bold text-white">
                        {unread}
                    </span>
                )}
            </button>

            {open && desktop && (
                <div className="absolute right-0 top-12 z-50 flex w-[380px] flex-col overflow-hidden rounded-2xl border border-line bg-surface pt-3 shadow-xl">
                    <div className="px-4 pb-2 text-sm font-bold">알림</div>
                    <NotificationPanel onNavigate={go} listClassName="max-h-[460px]"/>
                </div>
            )}
            {open && !desktop && (
                <MobileSheet onClose={() => setOpen(false)}>
                    <NotificationPanel onNavigate={go} listClassName="flex-1"/>
                </MobileSheet>
            )}
        </div>
    );
}
