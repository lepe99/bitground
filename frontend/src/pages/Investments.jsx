import React, { useState, useEffect, useContext } from "react";
import Sidebar from "../components/trade/Sidebar.jsx";
import PortfolioHeader from "../components/Investments/PortfolioHeader.jsx";
import AssetSummary from "../components/Investments/AssetSummary.jsx";
import HoldingsList from "../components/Investments/HoldingsList.jsx";
import TradeHistory from "../components/Investments/TradeHistory.jsx";
import PendingOrders from "../components/Investments/PendingOrders.jsx";
import { AuthContext } from "../auth/AuthContext";
import { TickerContext } from "../ticker/TickerProvider.jsx";
import {addFavorite, fetchFavorites, fetchOwnedAssets, removeFavorite} from "../api/assetApi.js";
import '../styles/Investments/Investments.css';
import "../styles/trade/Trade.css";
import Loading from "../components/Loading.jsx";
import { useNavigate } from 'react-router-dom';
import '../styles/trade/sidebar.css'

export default function Investments() {
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('보유자산');
    const [favoriteMarkets, setFavoriteMarkets] = useState([]);
    const [ownedMarkets, setOwnedMarkets] = useState([]);
    const [availableCash, setAvailableCash] = useState(0);
    const [userAssets, setUserAssets] = useState([]);
    const [cash, setCash] = useState(0);

    const { user } = useContext(AuthContext);
    const {
        markets,
        tickerMap,
        selectedMarket,
        setSelectedMarket,
        isWsConnected
    } = useContext(TickerContext);

    // ✅ 사용자 ID로 보유자산 + 캐시 불러오기
    useEffect(() => {
        if (!user?.id) return;

        const fetchAssetsAndFavorites = async () => {
            try {
                const { userAssets = [], cash = 0, availableCash = 0 } = (await fetchOwnedAssets()) || {};
                setUserAssets(userAssets);
                setCash(cash);
                setAvailableCash(availableCash);

                const symbols = userAssets.map(asset => asset.symbol);
                setOwnedMarkets(symbols);
            } catch (err) {
                console.error("보유 자산 불러오기 실패:", err);
                setUserAssets([]);
                setCash(0);
                setOwnedMarkets([]);
            }

            try {
                setFavoriteMarkets(await fetchFavorites());
            } catch {
                setFavoriteMarkets([]);
            }
        };

        fetchAssetsAndFavorites();
    }, [user?.id]);

    const toggleFavorite = (symbol) => {
        const isFav = favoriteMarkets.includes(symbol);
        const req = isFav ? removeFavorite(symbol) : addFavorite(symbol);
        req.then(() => {
            setFavoriteMarkets(prev =>
                isFav ? prev.filter(s => s !== symbol) : [...prev, symbol]
            );
        });
    };

    return (
        <div className="crypto-portfolio">
            {!isWsConnected && <Loading />}
            <div className="main-content">
                <PortfolioHeader activeTab={activeTab} setActiveTab={setActiveTab} />
                <div className="tab-content">
                    {activeTab === '보유자산' && (
                        <>
                            <AssetSummary
                                userAssets={userAssets}
                                cash={cash}
                                availableCash={availableCash}
                            />
                            <HoldingsList userAssets={userAssets} />
                        </>
                    )}
                    {activeTab === '거래내역' && <TradeHistory />}
                    {activeTab === '미체결' && <PendingOrders />}
                </div>
            </div>

            <aside className={"invest-sidebar"}>
                <Sidebar
                    markets={markets}
                    tickerMap={tickerMap}
                    ownedMarkets={ownedMarkets}
                    favoriteMarkets={favoriteMarkets}
                    selectedMarket={selectedMarket}
                    onSelectMarket={(market) => {
                        setSelectedMarket(market);
                        navigate(`/trade?market=${market}`); // ✅ 거래소 페이지로 이동
                    }}
                    onToggleFav={toggleFavorite}
                />
            </aside>
        </div>

    );
}
