import {useCallback, useContext, useEffect, useMemo, useState} from "react";
import {useNavigate} from "react-router-dom";
import {AuthContext} from "../auth/AuthContext.js";
import {TickerContext} from "../ticker/TickerContext.js";
import {useNotifications} from "../notifications/useNotifications.js";
import {addFavorite, fetchFavorites, fetchOwnedAssets, removeFavorite} from "../api/assetApi.js";
import {fetchPendingOrders} from "../api/orderApi.js";
import {buildPortfolio} from "../utils/portfolio.js";
import Loading from "../components/Loading.jsx";
import SegmentedTabs from "../components/ui/SegmentedTabs.jsx";
import MarketList from "../components/trade/MarketList.jsx";
import AssetOverview from "../components/investments/AssetOverview.jsx";
import HoldingsTable from "../components/investments/HoldingsTable.jsx";
import TradeHistory from "../components/investments/TradeHistory.jsx";
import PendingOrderList from "../components/investments/PendingOrderList.jsx";

/**
 * 투자내역: 보유자산(요약·비중·보유 코인) / 거래내역 / 미체결(예약 주문)
 * PC는 거래소처럼 오른쪽에 코인 목록(보유 탭)을 둔다
 */
export default function Investments() {
    const navigate = useNavigate();
    const {user} = useContext(AuthContext);
    const {markets, tickerMap, selectedMarket, setSelectedMarket, isWsConnected} = useContext(TickerContext);
    const {userCash} = useNotifications();

    const [tab, setTab] = useState("assets");
    const [assets, setAssets] = useState({userAssets: [], cash: 0, availableCash: 0});
    const [pending, setPending] = useState(null);
    const [favorites, setFavorites] = useState([]);

    const reload = useCallback(() => {
        fetchOwnedAssets()
            .then((data) => setAssets({userAssets: data?.userAssets ?? [], cash: data?.cash ?? 0, availableCash: data?.availableCash ?? 0}))
            .catch((error) => console.error("보유 자산 조회 실패:", error));
        fetchPendingOrders()
            .then((list) => setPending(Array.isArray(list) ? list : []))
            .catch(() => setPending([]));
    }, []);

    useEffect(() => {
        if (!user?.id) return;
        reload();
        fetchFavorites().then(setFavorites).catch(() => setFavorites([]));
    }, [user?.id, reload]);

    // 예약 주문이 체결되면 다시 읽는다
    useEffect(() => {
        if (userCash) reload();
    }, [userCash, reload]);

    const names = useMemo(() => Object.fromEntries(markets.map((m) => [m.market, m.name])), [markets]);
    const portfolio = buildPortfolio(assets.userAssets, tickerMap, names, assets.cash);
    const owned = portfolio.rows.map((r) => r.symbol);

    const toggleFav = (symbol) => {
        const isFav = favorites.includes(symbol);
        (isFav ? removeFavorite(symbol) : addFavorite(symbol))
            .then(() => setFavorites((prev) => (isFav ? prev.filter((s) => s !== symbol) : [...prev, symbol])))
            .catch((error) => console.error("관심 코인 변경 실패:", error));
    };

    const tabs = [
        {value: "assets", label: "보유자산"},
        {value: "history", label: "거래내역"},
        {value: "pending", label: "미체결", count: pending?.length ?? 0},
    ];

    return (
        <div className="flex flex-wrap gap-2 px-5 pt-2 pb-8 md:p-2">
            {!isWsConnected && <Loading/>}
            <div className="flex min-w-0 flex-[999_1_720px] flex-col gap-3 md:gap-2">
                <h1 className="pt-2 text-xl font-extrabold md:sr-only">투자내역</h1>
                <SegmentedTabs label="투자내역 보기" items={tabs} value={tab} onChange={setTab} fill className="md:rounded-xl md:border md:border-line md:bg-surface"/>

                {tab === "assets" && (
                    <>
                        <AssetOverview portfolio={portfolio} availableCash={assets.availableCash} reserved={Math.max(0, assets.cash - assets.availableCash)}/>
                        <HoldingsTable rows={portfolio.rows}/>
                    </>
                )}
                {tab === "history" && <TradeHistory/>}
                {tab === "pending" && <PendingOrderList orders={pending} tickerMap={tickerMap} onChanged={reload}/>}
            </div>

            <MarketList
                markets={markets}
                tickerMap={tickerMap}
                selectedMarket={selectedMarket}
                onSelectMarket={(market) => {
                    setSelectedMarket(market);
                    navigate(`/trade?market=${market}`);
                }}
                ownedMarkets={owned}
                favoriteMarkets={favorites}
                onToggleFav={toggleFav}
                initialFilter="owned"
                className="sticky top-[64px] h-[calc(100dvh-72px)] min-w-0 flex-[1_1_320px] overflow-hidden rounded-xl border border-line max-md:hidden"
            />
        </div>
    );
}
