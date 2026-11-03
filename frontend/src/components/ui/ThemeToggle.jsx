import {useTheme} from '../../theme/ThemeContext.js';

/** 라이트·다크 전환 버튼 */
export default function ThemeToggle({className = ''}) {
    const {theme, toggleTheme} = useTheme();
    const dark = theme === 'dark';

    return (
        <button
            type="button"
            onClick={toggleTheme}
            aria-label={dark ? '라이트 모드로 전환' : '다크 모드로 전환'}
            className={`flex size-10 items-center justify-center rounded-[10px] text-fg-2 hover:bg-surface-2 hover:text-fg md:border md:border-line ${className}`}
        >
            {dark ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="4"/>
                    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>
                </svg>
            ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>
                </svg>
            )}
        </button>
    );
}
