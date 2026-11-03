/** 'KRW-BTC' 또는 'BTC' → 'BTC' */
export const tickerOf = (symbol = '') => symbol.split('-').pop();

// 주요 코인 대표색. 없으면 심볼로 정한 중립색을 쓴다
const TINTS = {
    BTC: '#F7931A', ETH: '#627EEA', XRP: '#23292F', SOL: '#9945FF', DOGE: '#C2A633',
    ADA: '#0033AD', TRX: '#EB0029', LINK: '#2A5ADA', AVAX: '#E84142', SUI: '#4DA2FF',
    XLM: '#14B6E7', BCH: '#0AC18E', DOT: '#E6007A', HBAR: '#222222', POL: '#7B3FE4',
};
const FALLBACK = ['#4B5563', '#0F766E', '#7C3AED', '#B45309', '#1D4ED8', '#BE123C'];

/** 코인 대표색 (아이콘·비중 차트) */
export const coinTint = (symbol) => {
    const ticker = tickerOf(symbol);
    return TINTS[ticker] ?? FALLBACK[[...ticker].reduce((s, c) => s + c.charCodeAt(0), 0) % FALLBACK.length];
};
