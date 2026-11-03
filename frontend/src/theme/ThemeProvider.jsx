import {useCallback, useEffect, useMemo, useState} from 'react';
import {ThemeContext} from './ThemeContext.js';

const STORAGE_KEY = 'bg-theme';

// 저장된 값이 없으면 OS 설정을 따른다. (index.html의 인라인 스크립트와 같은 규칙)
const readTheme = () => {
    try {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved === 'light' || saved === 'dark') return saved;
    } catch {
        // 저장소를 못 쓰는 환경(사생활 보호 모드 등)
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
};

export function ThemeProvider({children}) {
    const [theme, setThemeState] = useState(readTheme);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
    }, [theme]);

    const setTheme = useCallback((next) => {
        setThemeState(next);
        try {
            localStorage.setItem(STORAGE_KEY, next);
        } catch {
            // 저장 실패해도 이번 방문 동안은 적용된다
        }
    }, []);

    const toggleTheme = useCallback(() => setTheme(theme === 'dark' ? 'light' : 'dark'), [theme, setTheme]);

    const value = useMemo(() => ({theme, setTheme, toggleTheme}), [theme, setTheme, toggleTheme]);
    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
