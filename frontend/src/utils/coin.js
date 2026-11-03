/** 'KRW-BTC' 또는 'BTC' → 'BTC' */
export const tickerOf = (symbol = '') => symbol.split('-').pop();
