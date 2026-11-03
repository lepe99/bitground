import {createContext, useContext} from 'react';

export const ThemeContext = createContext(null);

/** 현재 테마('light' | 'dark')와 전환 함수 */
export const useTheme = () => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme는 ThemeProvider 내부에서 사용되어야 합니다.');
    }
    return context;
};
