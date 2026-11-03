// 게시글 분류
export const CATEGORIES = [
    {value: 'ALL', label: '전체'},
    {value: 'CHAT', label: '잡담'},
    {value: 'QUESTION', label: '질문'},
    {value: 'INFO', label: '정보'},
];

export const CATEGORY_LABEL = {CHAT: '잡담', QUESTION: '질문', INFO: '정보'};

// 분류 표시 색 (Tailwind 클래스 이름을 그대로 써야 빌드에 포함된다)
export const CATEGORY_STYLE = {
    CHAT: 'bg-surface-2 text-fg-2',
    QUESTION: 'bg-down/10 text-down',
    INFO: 'bg-brand-soft text-brand-fg',
};

/** 오늘 쓴 글은 시각(14:05), 그 전은 날짜(11.02) */
export const postTime = (iso) => {
    if (!iso) return '';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return d.toDateString() === new Date().toDateString() ? `${p(d.getHours())}:${p(d.getMinutes())}` : `${p(d.getMonth() + 1)}.${p(d.getDate())}`;
};

/** 2026.11.03 14:05 */
export const fullTime = (iso) => {
    if (!iso) return '';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};
