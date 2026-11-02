import React, { useContext, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { TickerContext } from "../../ticker/TickerProvider.jsx";
import { VscHeart, VscHeartFilled } from 'react-icons/vsc';
import { AiOutlineSearch } from 'react-icons/ai';
import '../../styles/trade/sidebar.css';

export default function Sidebar() {
    const {
        markets = [],
        tickerMap = {},
        selectedMarket = '',
        setSelectedMarket = () => {},
        favoriteMarkets = [],
        ownedMarkets = [],
        toggleFavorite = () => {},
    } = useContext(TickerContext);

    const navigate = useNavigate();

    const [searchTerm, setSearchTerm] = useState('');
    const [sortKey, setSortKey] = useState('name');
    const [sortOrder, setSortOrder] = useState('asc');
    const [filterKey, setFilterKey] = useState('all');

    const getColor = amt => amt > 0 ? '#fc5754' : amt < 0 ? '#3EB2FF' : '#8c8c8c';
    const term = searchTerm.trim().toLowerCase();

    const baseList = useMemo(() => {
        return markets
            .filter(({ name, market }) => (
                name.toLowerCase().includes(term) || market.toLowerCase().includes(term)
            ))
            .sort((a, b) => {
                let va, vb;
                if (sortKey === 'price') {
                    va = tickerMap[a.market]?.price ?? 0;
                    vb = tickerMap[b.market]?.price ?? 0;
                } else if (sortKey === 'changeRate') {
                    va = tickerMap[a.market]?.changeRate ?? 0;
                    vb = tickerMap[b.market]?.changeRate ?? 0;
                } else {
                    va = a.name;
                    vb = b.name;
                }
                return (va < vb ? -1 : 1) * (sortOrder === 'asc' ? 1 : -1);
            });
    }, [markets, tickerMap, searchTerm, sortKey, sortOrder]);

    const displayList = useMemo(() => {
        if (filterKey === 'owned') return baseList.filter(i => ownedMarkets.includes(i.market));
        if (filterKey === 'fav') return baseList.filter(i => favoriteMarkets.includes(i.market));
        return baseList;
    }, [baseList, filterKey, ownedMarkets, favoriteMarkets]);

    const onSort = key => {
        if (sortKey === key) setSortOrder(o => o === 'asc' ? 'desc' : 'asc');
        else {
            setSortKey(key);
            setSortOrder('asc');
        }
    };

    return (
        <div className="sidebar-wrapper">
            <div className="sidebar-header">
                <div className="search-box">
                    <AiOutlineSearch className="icon-search" />
                    <input
                        type="text"
                        placeholder="코인명 검색"
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                    />
                </div>
                <div className="side-tabs">
                    {['all', 'owned', 'fav'].map(key => (
                        <button
                            key={key}
                            className={`side-tab ${filterKey === key ? 'active' : ''}`}
                            onClick={() => setFilterKey(key)}
                        >
                            {key === 'all' ? '원화' : key === 'owned' ? '보유' : '관심'}
                        </button>
                    ))}
                </div>
            </div>

            <div className="table-container">
                <table className="ticker-table">
                    <colgroup>
                        <col className="col-name" />
                        <col className="col-price" />
                        <col className="col-change" />
                    </colgroup>
                    <thead>
                    <tr>
                        <th>이름</th>
                        <th onClick={() => onSort('price')}>현재가 {sortKey === 'price' && (sortOrder === 'asc' ? '▲' : '▼')}</th>
                        <th onClick={() => onSort('changeRate')}>전일대비 {sortKey === 'changeRate' && (sortOrder === 'asc' ? '▲' : '▼')}</th>
                    </tr>
                    </thead>
                    <tbody className="scroll-body">
                    {displayList.map(({ market, name }) => {
                        const t = tickerMap[market] || {};
                        const price = t.price != null ? t.price.toLocaleString() : '—';
                        const changeAmt = t.changeAmt != null ? t.changeAmt.toLocaleString() : '—';
                        const changeRate = t.changeRate != null ? (t.changeRate * 100).toFixed(2) + '%' : '—';
                        const color = t.changeAmt != null ? getColor(t.changeAmt) : '#343434';
                        const isFav = favoriteMarkets.includes(market);
                        return (
                            <tr
                                key={market}
                                className={selectedMarket === market ? 'selected' : ''}
                                onClick={() => {
                                    setSelectedMarket(market);
                                    navigate(`/trade?market=${market}`); // ✅ 거래소 페이지로 이동
                                }}
                            >
                                <td className="cell-name">
                                    <button className="btn-heart" onClick={e => {
                                        e.stopPropagation();
                                        toggleFavorite(market); // ✅ 여기 수정됨
                                    }}>
                                        {isFav
                                            ? <VscHeartFilled className="icon-heart filled" />
                                            : <VscHeart className="icon-heart" />}
                                    </button>
                                    <div className="info">
                                        <div className="name">{name}</div>
                                        <div className="code">{market}</div>
                                    </div>
                                </td>
                                <td className="cell-price" style={{ color }}>
                                    {price}
                                </td>
                                <td className="cell-change">
                                    <span className="rate" style={{ color }}>{changeRate}</span>
                                    <span className="amt" style={{ color }}>({changeAmt})</span>
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
