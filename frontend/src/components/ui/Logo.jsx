/**
 * Bitground 로고. B 윤곽은 글자색(currentColor), 상승 막대 3개는 브랜드 색을 따라 테마에 맞춰 바뀐다.
 * @param withText 옆에 'Bitground' 글자를 붙일지
 */
export default function Logo({size = 28, withText = true, className = ''}) {
    return (
        <span className={`inline-flex items-center gap-2 ${className}`}>
            <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden={withText || undefined} role={withText ? undefined : 'img'} aria-label={withText ? undefined : 'Bitground'}>
                <path
                    d="M5.5 15 V4.3 H18 A5.4 5.4 0 0 1 18 15.1 H13.6 M13.6 15.1 H19.2 A6.4 6.4 0 0 1 19.2 27.9 H13.6"
                    fill="none" stroke="currentColor" strokeWidth="2.6"
                />
                <rect x="4.2" y="18.5" width="2.6" height="10.7" className="fill-logo-bar"/>
                <rect x="8.1" y="13.8" width="2.6" height="15.4" className="fill-logo-bar"/>
                <rect x="12.3" y="9.2" width="2.6" height="20" className="fill-logo-bar"/>
            </svg>
            {withText && <span className="text-lg font-extrabold tracking-tight">Bitground</span>}
        </span>
    );
}
