import React, {useContext, useEffect, useState} from "react";
import Sidebar from "../components/trade/Sidebar";
import CoinDetail from "../components/trade/CoinDetail";
import OrderBox from "../components/trade/OrderBox";
import TradeHistory from "../components/trade/TradeHistory";
import "../styles/trade/Trade.css";
import TradingViewWidget from "../components/trade/TradingViewWidget";
import api from "../api/axiosConfig.js";
import {AuthContext} from "../auth/AuthContext.js";
import {TickerContext} from "../ticker/TickerProvider.jsx";
import {useToast} from "../components/Toast.jsx";
import Loading from "../components/Loading.jsx";

export default function Trade() {
    const [favoriteMarkets, setFavoriteMarkets] = useState([]);
    const [owned, setOwned] = useState([]);
    const {user} = useContext(AuthContext)
    const {markets, selectedMarket, tickerMap, setSelectedMarket, isWsConnected} = useContext(TickerContext);
    const selectedMarketName = markets.find(m => m.market === selectedMarket)?.name;
    const [cash, setCash] = useState(user.cash);
    const [holdings, setHoldings] = useState('');
    const [orderTab, setOrderTab] = useState('BUY'); // BUY or SELL
    const [userAssets, setUserAssets] = useState({});

    const { userCash } = useToast();

    const fetchOwned = async () => {
        try {
            const res = await api.get(`/assets/owned`, { withCredentials: true });
            const userAssets = res.data.userAssets || [];
            const symbols = userAssets.map(asset => asset.symbol);
            return symbols;
        } catch (err) {
            console.error("보유 코인 불러오기 실패:", err);
            return [];
        }
    };

    const handleOrderPlaced = () => {
        api.get("/assets")
            .then(async res => {
                setCash(res.data.cash);

                const userAssetsMap = res.data.userAssets.reduce((acc, asset) => {
                    acc[asset.symbol] = { amount: asset.amount, avgPrice: asset.avgPrice };
                    return acc;
                }, {});
                setUserAssets(userAssetsMap);

                const symbols = await fetchOwned();
                setOwned(symbols);
            })
            .catch(console.error);
    };

    useEffect(() => {
        if (userCash === 0) return;
        // 예약 주문이 완료되면 자산 정보 갱신
        handleOrderPlaced();
    }, [userCash]);


    useEffect(() => {
        api.get('/favorites', {params: {userId: user.id}})
            .then(res => setFavoriteMarkets(res.data));
        handleOrderPlaced(); // 초기 자산 정보 로딩
    }, [user.id]);

    useEffect(() => {
        if (!selectedMarket) return;
        // 선택된 마켓의 자산 정보 갱신
        setHoldings(userAssets[selectedMarket]?.amount || 0);
    }, [selectedMarket, userAssets]);


    const toggleFav = symbol => {
        const isFav = favoriteMarkets.includes(symbol);
        const req = isFav
            ? api.delete(`/favorites/${symbol}`, {params: {userId: user.id}})
            : api.post('/favorites', null, {params: {userId: user.id, symbol}});

        req.then(() => {
            setFavoriteMarkets(prev =>
                isFav ? prev.filter(s => s !== symbol) : [...prev, symbol]
            );
        });
    };

    return (
        <div className="trade-page">
            {/* loading overlay */}
            {!isWsConnected && (
                <Loading/>
            )}
            <div className="trade-page__content">
                <main className="main">
                    <section className="main__detail">
                        <div className="coin-detail">
                            <CoinDetail
                                market={selectedMarket}
                                marketName={selectedMarketName}
                                data={tickerMap[selectedMarket]}
                                favoriteMarkets={favoriteMarkets}
                                onToggleFav={toggleFav}
                            />
                        </div>
                        <div className="chart-widget">
                            <TradingViewWidget market={selectedMarket}/>
                        </div>
                    </section>
                    <div className="bottom-box-set">
                        <section className={`order-box ${orderTab === 'SELL' ? 'sell' : 'buy'}`}>
                            <OrderBox
                                tickerMap={tickerMap}
                                selectedMarket={selectedMarket}
                                onOrderPlaced={handleOrderPlaced}
                                cash={cash}
                                holdings={holdings}
                                onTradeTabChange={setOrderTab}
                            />
                        </section>
                        <section className="trade-history">
                            <TradeHistory
                                symbol={selectedMarket}/>
                        </section>
                    </div>
                </main>
                <aside className="sidebar">
                    <Sidebar
                        markets={markets}
                        tickerMap={tickerMap}
                        ownedMarkets={owned}
                        onSelectMarket={setSelectedMarket}
                        selectedMarket={selectedMarket}
                        favoriteMarkets={favoriteMarkets}
                        onToggleFav={toggleFav}
                    />
                </aside>
            </div>
        </div>
    );
}