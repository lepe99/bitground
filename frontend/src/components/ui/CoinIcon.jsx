import {coinTint, tickerOf} from '../../utils/coin.js';

/** 코인 아이콘 (원 안에 심볼). 이미지 대신 색으로 구분한다 */
export default function CoinIcon({symbol, size = 36, className = ''}) {
    const ticker = tickerOf(symbol);
    const tint = coinTint(symbol);
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
