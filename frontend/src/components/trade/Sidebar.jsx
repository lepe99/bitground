import React, {useState, useMemo, useRef, useEffect} from 'react';
import {VscHeart, VscHeartFilled} from "react-icons/vsc";
import '../../styles/trade/sidebar.css';
import {AiOutlineSearch} from "react-icons/ai";

export default function Sidebar({
                                    markets,
                                    tickerMap,
                                    onSelectMarket,
                                    selectedMarket,
                                    ownedMarkets = [],
                                    favoriteMarkets = [],
                                    onToggleFav
                                }) {
    const [searchTerm, setSearchTerm] = useState('');
    const [sortKey, setSortKey] = useState('name');
    const [sortOrder, setSortOrder] = useState('asc');
    const [filterKey, setFilterKey] = useState('all');
    const [priceBorders, setPriceBorders] = useState({});
    const prevPricesRef = useRef({});

    const getColor = amt => amt > 0 ? '#fc5754' : amt < 0 ? '#2979ff' : '#8c8c8c';
    const term = searchTerm.trim().toLowerCase();

    // 1) 검색+정렬
    const baseList = useMemo(() => {
        return markets
            .filter(({name, market}) => {
                // name, market 모두 소문자로 바꿔서 포함 여부 검사
                return (
                    name.toLowerCase().includes(term) ||
                    market.toLowerCase().includes(term)
                );
            })
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
                if (va < vb) return sortOrder === 'asc' ? -1 : 1;
                if (va > vb) return sortOrder === 'asc' ? 1 : -1;
                return 0;
            });
    }, [markets, tickerMap, searchTerm, sortKey, sortOrder]);

    // 2) 탭 필터
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

    const formatPrice = (value) => {
        if (value == null) return '—';
        const str = value.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 });
        return str.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, ''); // 뒤쪽 0 제거
    };

    useEffect(() => {
        const newBorders = {};

        for (const market of markets.map(m => m.market)) {
            const prev = prevPricesRef.current[market];
            const curr = tickerMap[market]?.price;

            if (curr != null && prev != null && curr !== prev) {
                newBorders[market] = curr > prev ? 'up' : 'down';

                // 0.5초 후 해당 마켓의 테두리 제거
                setTimeout(() => {
                    setPriceBorders(prev => ({...prev, [market]: null}));
                }, 500);
            }

            if (curr != null) {
                prevPricesRef.current[market] = curr;
            }
        }

        if (Object.keys(newBorders).length > 0) {
            setPriceBorders(prev => ({...prev, ...newBorders}));
        }
    }, [tickerMap]);

    return (
        <div className="sidebar-wrapper">
            <div className="sidebar-header">
                {/* 검색창 */}
                <div className="search-box">
                    <AiOutlineSearch className="icon-search"/>
                    <input
                        type="text"
                        placeholder="코인명 검색"
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                    />
                </div>
                {/* 탭 */}
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
                        <col className="col-name"/>
                        <col className="col-price"/>
                        <col className="col-change"/>
                    </colgroup>
                    <thead>
                    <tr>
                        <th onClick={() => onSort('name')}>
                            이름 {sortKey === 'name' && (sortOrder === 'asc' ? '▲' : '▼')}
                        </th>
                        <th onClick={() => onSort('price')}>
                            현재가 {sortKey === 'price' && (sortOrder === 'asc' ? '▲' : '▼')}
                        </th>
                        <th onClick={() => onSort('changeRate')}>
                            전일대비 {sortKey === 'changeRate' && (sortOrder === 'asc' ? '▲' : '▼')}
                        </th>
                    </tr>
                    </thead>
                    <tbody className={"scroll-body"}>
                    {displayList.map(({market, name}) => {
                        const t = tickerMap[market] || {};
                        const price = formatPrice(t.price);
                        const changeAmt = t.changeAmt != null ? t.changeAmt.toLocaleString() : '—';
                        const changeRate = t.changeRate != null ? (t.changeRate * 100).toFixed(2) + '%' : '—';
                        const color = t.changeAmt != null ? getColor(t.changeAmt) : '#343434';
                        const isFav = favoriteMarkets.includes(market);
                        return (
                            <tr
                                key={market}
                                className={selectedMarket === market ? 'selected' : ''}
                                onClick={() => onSelectMarket(market)}
                            >
                                <td className="cell-name">
                                    <button className="btn-heart" onClick={e => {
                                        e.stopPropagation();
                                        onToggleFav(market);
                                    }}>
                                        {isFav
                                            ? <VscHeartFilled className="icon-heart filled"/>
                                            : <VscHeart className="icon-heart"/>}
                                    </button>
                                    <div className="info">
                                        <div className="name">{name}</div>
                                        <div className="code">{market}</div>
                                    </div>
                                </td>
                                <td className="cell-price">
                                  <span
                                      className={`price-box ${priceBorders[market] === 'up' ? 'highlight-up' : priceBorders[market] === 'down' ? 'highlight-down' : ''}`} style={{color}}>
                                    {price}
                                  </span>
                                </td>
                                <td className="cell-change">
                  <span className="rate" style={{color}}>
                    {changeRate}
                  </span>
                                    <span className="amt" style={{color}}>
                    ({changeAmt})
                  </span>
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
