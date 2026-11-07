// 알림 종류 아이콘 (kindOf의 icon·tone)
const ICON_PATHS = {
    trade: <><path d="M7 17L17 7"/><path d="M9 7h8v8"/></>,
    comment: <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>,
    inquiry: <><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.5V14"/><path d="M12 17.2v.1"/></>,
    notice: <><path d="M4 10v4h3l6 4V6L7 10H4z"/><path d="M17 9a4 4 0 0 1 0 6"/></>,
    season: <><path d="M8 4h8v5a4 4 0 0 1-8 0V4z"/><path d="M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4"/><path d="M12 13v4M9 20h6"/></>,
};

export default function KindIcon({kind, size = 'size-9'}) {
    return (
        <span className={`flex ${size} shrink-0 items-center justify-center rounded-full ${kind.tone}`}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                {ICON_PATHS[kind.icon]}
            </svg>
        </span>
    );
}
