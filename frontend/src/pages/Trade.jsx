import {useCallback, useContext, useEffect, useState} from "react";
import {useSearchParams} from "react-router-dom";
import {addFavorite, fetchAvailableAssets, fetchFavorites, fetchOwnedAssets, removeFavorite} from "../api/assetApi.js";
import {AuthContext} from "../auth/AuthContext.js";
import {TickerContext} from "../ticker/TickerContext.js";
import {useNotifications} from "../notifications/useNotifications.js";
import {useIsMobile} from "../hooks/useMediaQuery.js";
import Loading from "../components/Loading.jsx";
import MarketList from "../components/trade/MarketList.jsx";
import CoinSummary from "../components/trade/CoinSummary.jsx";
import TradingViewWidget from "../components/trade/TradingViewWidget.jsx";
import OrderPanel from "../components/trade/OrderPanel.jsx";
import RecentTrades from "../components/trade/RecentTrades.jsx";
import MobileCoinDetail from "../components/trade/MobileCoinDetail.jsx";

/**
 * 거래소
 * - PC: 업비트처럼 시세·차트·주문·체결을 한 화면에, 오른쪽에 코인 목록
 * - 모바일: 코인 목록 → (?market=) 코인 상세 → 아래 시트에서 주문
 */
export default function Trade() {
    const {user} = useContext(AuthContext);
    const {markets, selectedMarket, tickerMap, setSelectedMarket, isWsConnected} = useContext(TickerContext);
    const {userCash} = useNotifications();
    const isMobile = useIsMobile();
    const [searchParams, setSearchParams] = useSearchParams();
    const marketParam = searchParams.get("market");

    const [favorites, setFavorites] = useState([]);
    const [owned, setOwned] = useState([]);
    const [cash, setCash] = useState(user.cash ?? 0);
    const [assets, setAssets] = useState({}); // symbol → {amount, avgPrice} (매도 가능 수량 기준)

    // 다른 화면에서 /trade?market=KRW-BTC 로 들어오면 그 종목을 연다
    useEffect(() => {
        if (marketParam) setSelectedMarket(marketParam);
    }, [marketParam, setSelectedMarket]);

    const selectMarket = useCallback((market) => {
        setSelectedMarket(market);
        setSearchParams({market}, {replace: !isMobile});
    }, [setSelectedMarket, setSearchParams, isMobile]);

    // 주문 후·예약 체결 알림 후 자산을 다시 읽는다
    const reloadAssets = useCallback(async () => {
        try {
            const [available, ownedAssets] = await Promise.all([fetchAvailableAssets(), fetchOwnedAssets()]);
            setCash(available.cash);
            setAssets(Object.fromEntries(available.userAssets.map((a) => [a.symbol, {amount: Number(a.amount), avgPrice: Number(a.avgPrice)}])));
            setOwned((ownedAssets.userAssets ?? []).map((a) => a.symbol));
        } catch (error) {
            console.error("자산 정보 불러오기 실패:", error);
        }
    }, []);

    useEffect(() => {
        fetchFavorites().then(setFavorites).catch(() => setFavorites([]));
        void reloadAssets();
    }, [user.id, reloadAssets]);

    useEffect(() => {
        if (userCash) void reloadAssets();
    }, [userCash, reloadAssets]);

    const toggleFav = (symbol) => {
        const isFav = favorites.includes(symbol);
        (isFav ? removeFavorite(symbol) : addFavorite(symbol))
            .then(() => setFavorites((prev) => (isFav ? prev.filter((s) => s !== symbol) : [...prev, symbol])))
            .catch((error) => console.error("관심 코인 변경 실패:", error));
    };

    const name = markets.find((m) => m.market === selectedMarket)?.name;
    const ticker = tickerMap[selectedMarket];
    const holding = assets[selectedMarket];

    const list = (className) => (
        <MarketList
            markets={markets}
            tickerMap={tickerMap}
            selectedMarket={selectedMarket}
            onSelectMarket={selectMarket}
            ownedMarkets={owned}
            favoriteMarkets={favorites}
            onToggleFav={toggleFav}
            className={className}
        />
    );

    if (isMobile) {
        return (
            <div >
                {!isWsConnected && <Loading/>}
                {marketParam ? (
                    <MobileCoinDetail
                        market={selectedMarket}
                        name={name}
                        ticker={ticker}
                        isFav={favorites.includes(selectedMarket)}
                        onToggleFav={toggleFav}
                        cash={cash}
                        holding={holding}
                        onOrderPlaced={reloadAssets}
                    />
                ) : (
                    list("h-[calc(100dvh-56px-56px-env(safe-area-inset-bottom))]")
                )}
            </div>
        );
    }

    return (
        <div className="flex flex-wrap gap-2 p-2">
            {!isWsConnected && <Loading/>}
            <div className="flex min-w-0 flex-[999_1_720px] flex-col gap-2">
                <CoinSummary
                    market={selectedMarket}
                    name={name}
                    ticker={ticker}
                    isFav={favorites.includes(selectedMarket)}
                    onToggleFav={toggleFav}
                />
                <section className="overflow-hidden rounded-xl border border-line bg-surface">
                    <TradingViewWidget market={selectedMarket} height={420}/>
                </section>
                <div className="flex flex-wrap gap-2">
                    <OrderPanel
                        market={selectedMarket}
                        currentPrice={ticker?.price ?? 0}
                        cash={cash}
                        holdings={holding?.amount ?? 0}
                        onOrderPlaced={reloadAssets}
                        className="min-w-0 flex-[1_1_340px]"
                    />
                    <RecentTrades symbol={selectedMarket} className="h-[468px] min-w-0 flex-[1_1_340px]"/>
                </div>
            </div>
            {list("sticky top-[64px] h-[calc(100dvh-72px)] min-w-0 flex-[1_1_340px] overflow-hidden rounded-xl border border-line")}
        </div>
    );
}
