// 관리자 화면 공통 표시

export const PROVIDERS = {google: '구글', naver: '네이버', kakao: '카카오'};

/** 2026-11-06T10:05:00 → 11.06 10:05 */
export const shortTime = (iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    const p = (n) => String(n).padStart(2, '0');
    return `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export const shortDate = (iso) => (iso ? iso.slice(0, 10).replaceAll('-', '.') : '—');

// 작업 기록의 작업 이름
export const ACTIONS = {
    USER_SUSPEND: '이용 정지',
    USER_UNSUSPEND: '정지 해제',
    POST_DELETE: '게시글 삭제',
    COMMENT_DELETE: '댓글 삭제',
    NOTICE_CREATE: '공지 등록',
    NOTICE_UPDATE: '공지 수정',
    NOTICE_DELETE: '공지 삭제',
};
