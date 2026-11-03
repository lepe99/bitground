/**
 * 쪽 번호 (0부터 시작). 현재 쪽 주변 5개만 보여준다
 */
export default function Pagination({page, totalPages, onChange, className = ''}) {
    if (totalPages <= 1) return null;
    const size = 5;
    let start = Math.max(0, page - Math.floor(size / 2));
    const end = Math.min(totalPages, start + size);
    start = Math.max(0, end - size);
    const pages = Array.from({length: end - start}, (_, i) => start + i);
    const btn = 'flex size-9 items-center justify-center rounded-[10px] text-sm';

    return (
        <nav aria-label="쪽 이동" className={`flex items-center justify-center gap-1 ${className}`}>
            <button type="button" onClick={() => onChange(page - 1)} disabled={page === 0} aria-label="이전 쪽" className={`${btn} text-fg-2 hover:bg-surface-2 disabled:opacity-30`}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>
            </button>
            {pages.map((p) => (
                <button
                    key={p}
                    type="button"
                    onClick={() => onChange(p)}
                    aria-current={p === page ? 'page' : undefined}
                    className={`num ${btn} ${p === page ? 'bg-fg font-bold text-bg' : 'text-fg-2 hover:bg-surface-2'}`}
                >
                    {p + 1}
                </button>
            ))}
            <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1} aria-label="다음 쪽" className={`${btn} text-fg-2 hover:bg-surface-2 disabled:opacity-30`}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>
            </button>
        </nav>
    );
}
