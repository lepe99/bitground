import {tickerOf} from '../../utils/coin.js';

// 주요 코인 대표색. 없으면 심볼로 정한 중립색을 쓴다
const TINTS = {
    BTC: '#F7931A', ETH: '#627EEA', XRP: '#23292F', SOL: '#9945FF', DOGE: '#C2A633',
    ADA: '#0033AD', TRX: '#EB0029', LINK: '#2A5ADA', AVAX: '#E84142', SUI: '#4DA2FF',
    XLM: '#14B6E7', BCH: '#0AC18E', DOT: '#E6007A', HBAR: '#222222', POL: '#7B3FE4',
};
const FALLBACK = ['#4B5563', '#0F766E', '#7C3AED', '#B45309', '#1D4ED8', '#BE123C'];

/** 코인 아이콘 (원 안에 심볼). 이미지 대신 색으로 구분한다 */
export default function CoinIcon({symbol, size = 36, className = ''}) {
    const ticker = tickerOf(symbol);
    const tint = TINTS[ticker] ?? FALLBACK[[...ticker].reduce((s, c) => s + c.charCodeAt(0), 0) % FALLBACK.length];
    const fontSize = ticker.length > 3 ? size * 0.26 : size * 0.32;

    return (
        <span
            aria-hidden="true"
            className={`inline-flex shrink-0 items-center justify-center rounded-full font-extrabold text-white ${className}`}
            style={{width: size, height: size, background: tint, fontSize}}
        >
            {ticker.slice(0, 4)}
        </span>
    );
}
