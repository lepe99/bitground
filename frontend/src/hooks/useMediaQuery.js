import {useEffect, useState} from 'react';

/** CSS 미디어 쿼리 일치 여부 (예: '(max-width: 767px)') */
export function useMediaQuery(query) {
    const [matches, setMatches] = useState(() => window.matchMedia(query).matches);

    useEffect(() => {
        const mql = window.matchMedia(query);
        const onChange = () => setMatches(mql.matches);
        onChange();
        mql.addEventListener('change', onChange);
        return () => mql.removeEventListener('change', onChange);
    }, [query]);

    return matches;
}

/** Tailwind md(768px) 미만 */
export const useIsMobile = () => useMediaQuery('(max-width: 767px)');
