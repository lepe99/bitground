import {useEffect, useMemo, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import CoinIcon from '../ui/CoinIcon.jsx';
import TierBadge from '../ui/TierBadge.jsx';
import {useAuth} from '../../auth/useAuth.js';
import {useUpbitTickers} from '../../hooks/useUpbitTickers.js';
import {fetchOwnedAssets} from '../../api/assetApi.js';
import {fetchCurrentRanking} from '../../api/rankApi.js';
import {fetchCoinSymbols} from '../../api/coinApi.js';
import {TIER_NAMES} from '../../constants/tiers.js';
import {changeColor, formatKRW, formatPercent, formatSignedKRW} from '../../utils/format.js';

/**
 * 로그인 후 첫 화면 위쪽: 내 자산 · 이번 시즌 순위 · (모바일) 보유 코인
 */
export default function MySummary() {
    const {user} = useAuth();
    const [assets, setAssets] = useState(null);
    const [rank, setRank] = useState(null);
    const [names, setNames] = useState({});

    useEffect(() => {
        fetchOwnedAssets()
            .then(setAssets)
            .catch((error) => console.error('보유 자산 조회 실패:', error));
        fetchCoinSymbols().then((list) => setNames(Object.fromEntries(list.map((c) => [c.symbol, c.koreanName]))));
    }, []);

    useEffect(() => {
        if (!user?.id) return;
        fetchCurrentRanking()
            .then((list) => {
                const mine = list.find((r) => r.userId === user.id);
                setRank(mine ? {rank: mine.ranks, total: list.length, tier: mine.tier, season: mine.seasonName} : {rank: null, total: list.length});
            })
            .catch((error) => console.error('랭킹 조회 실패:', error));
    }, [user?.id]);

    const holdings = useMemo(() => assets?.userAssets ?? [], [assets]);
    const tickers = useUpbitTickers(holdings.map((h) => h.symbol));

    const rows = holdings
        .map((h) => {
            const amount = Number(h.amount);
            const buy = amount * Number(h.avgPrice);
            const price = tickers[h.symbol]?.price;
            const value = price != null ? amount * price : buy;
            return {symbol: h.symbol, amount, buy, value, rate: buy ? ((value - buy) / buy) * 100 : 0};
        })
        .sort((a, b) => b.value - a.value);

    const cash = Number(assets?.cash ?? 0);
    const evalTotal = rows.reduce((s, r) => s + r.value, 0);
    const buyTotal = rows.reduce((s, r) => s + r.buy, 0);
    const pnl = evalTotal - buyTotal;
    const total = cash + evalTotal;
    const tier = rank?.tier ?? user?.currentTier ?? user?.tier ?? 0;
    const percentile = rank?.rank && rank.total ? Math.max(1, Math.round((rank.rank / rank.total) * 100)) : null;

    return (
        <div className="flex flex-col gap-3 md:gap-5">
            <section className="flex flex-col gap-1 py-2 md:py-0">
                <span className="text-[15px] text-fg-2">{user?.name ? `${user.name}님의 자산` : '내 자산'}</span>
                <span className="num text-[32px] font-extrabold tracking-[-0.02em] md:text-[40px]">
                    {assets ? `${formatKRW(total)}원` : '—'}
                </span>
                {assets && rows.length > 0 && (
                    <span className={`num text-[15px] font-semibold ${changeColor(pnl)}`}>
                        {formatSignedKRW(pnl)}원 ({formatPercent(buyTotal ? (pnl / buyTotal) * 100 : 0)})
                    </span>
                )}
            </section>

            <Card as={Link} to="/rank" className="flex items-center gap-3.5 p-5">
                <TierBadge tier={tier} size={44}/>
                <span className="flex flex-1 flex-col gap-1">
                    <span className="text-[13px] text-fg-3">{rank?.season ?? '이번 시즌'} · {TIER_NAMES[tier] ?? '순위 집계 전'}</span>
                    <span className="num text-[17px] font-bold">
                        {rank?.rank ? `${rank.rank}위 · 상위 ${percentile}%` : '첫 거래를 하면 순위에 올라요'}
                    </span>
                </span>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-fg-3" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>
            </Card>

            <div className="hidden gap-3 md:flex">
                <Button as={Link} to="/trade" size="lg" className="h-[52px] px-6 text-base">거래하러 가기</Button>
                <Button as={Link} to="/investments" variant="secondary" size="lg" className="h-[52px] px-6 text-base font-semibold">투자내역</Button>
            </div>

            {/* 모바일: 보유 코인 (PC는 투자내역 화면에서 본다) */}
            <Card className="flex flex-col px-5 pt-5 pb-2 md:hidden">
                <div className="flex items-baseline justify-between pb-2">
                    <h2 className="text-lg font-bold">보유 코인</h2>
                    <span className="num text-[13px] text-fg-3">주문 가능 {formatKRW(assets?.availableCash)}원</span>
                </div>
                {rows.length === 0 && <p className="py-6 text-center text-sm text-fg-3">아직 보유한 코인이 없어요.</p>}
                <ul>
                    {rows.map((r) => (
                        <li key={r.symbol}>
                            <Link to={`/trade?market=${r.symbol}`} className="flex items-center gap-3.5 py-3">
                                <CoinIcon symbol={r.symbol} size={40}/>
                                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                                    <span className="text-base font-semibold">{names[r.symbol] ?? r.symbol}</span>
                                    <span className="num text-[13px] text-fg-3">{r.amount} {r.symbol.split('-').pop()}</span>
                                </span>
                                <span className="flex flex-col items-end gap-0.5">
                                    <span className="num text-base font-semibold">{formatKRW(r.value)}원</span>
                                    <span className={`num text-[13px] font-semibold ${changeColor(r.rate)}`}>{formatPercent(r.rate)}</span>
                                </span>
                            </Link>
                        </li>
                    ))}
                </ul>
                <Link to="/investments" className="border-t border-line py-3 text-center text-sm font-semibold text-fg-2">투자내역 전체 보기</Link>
            </Card>
        </div>
    );
}
