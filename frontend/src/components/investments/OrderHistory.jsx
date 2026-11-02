import { useState, useEffect, useContext } from 'react';
import { AuthContext } from "../../auth/AuthContext"; // 사용자 인증 정보
import {fetchSeasons} from "../../api/seasonApi.js";
import {fetchSeasonOrders} from "../../api/orderApi.js";

// 🔢 숫자 포맷 함수 (소수점 자리 자동 조정)
function formatNumber(value, digits = undefined, trimZeros = true) {
    if (isNaN(value)) return '-';

    const num = Number(value);
    const fractionDigits = digits ?? (num < 1 ? 8 : 2);

    return num.toLocaleString(undefined, {
        minimumFractionDigits: trimZeros ? 0 : fractionDigits,
        maximumFractionDigits: fractionDigits,
    });
}

export default function OrderHistory() {
    const { user } = useContext(AuthContext); // 로그인된 사용자 정보

    // 📦 상태값 정의
    const [seasonOptions, setSeasonOptions] = useState([]);        // 시즌 목록
    const [selectedSeasonId, setSelectedSeasonId] = useState(null); // 선택된 시즌 ID
    const [selectedType, setSelectedType] = useState('전체');       // 주문 타입 필터
    const [searchKeyword, setSearchKeyword] = useState('');         // 검색 키워드
    const [orders, setOrders] = useState([]);                       // 주문 내역

    // 📘 주문 타입 한글 ↔ 영어 매핑
    const typeMap = {
        전체: null,
        매수: "BUY",
        매도: "SELL"
    };

    // 📅 시즌 목록 가져오기 (최초 실행 시)
    useEffect(() => {
        fetchSeasons()
            .then(seasons => {
                setSeasonOptions(seasons);
                setSelectedSeasonId(seasons[0]?.id || null); // 첫 번째 시즌 자동 선택
            })
            .catch(err => console.error('시즌 목록 로딩 실패:', err));
    }, []);

    // 📦 선택된 시즌 + 로그인 유저 기준으로 주문 내역 조회
    useEffect(() => {
        if (!selectedSeasonId || !user?.id) return;

        fetchSeasonOrders(selectedSeasonId)
            .then(setOrders)
            .catch(err => {
                console.error('주문 내역 로딩 실패:', err);
                setOrders([]);
            });
    }, [selectedSeasonId, user]);

    // 🔍 필터 적용된 주문 내역 계산
    const filteredOrders = orders
        .filter(order => order.status !== 'PENDING') // 미체결 제외
        .filter(order => {
            const matchesType = !typeMap[selectedType] || order.orderType === typeMap[selectedType];
            const matchesSearch =
                searchKeyword === '' ||
                order.coinName?.includes(searchKeyword) ||
                order.symbol?.includes(searchKeyword);
            return matchesType && matchesSearch;
        })
        .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt)); // 최신순 정렬

    function formatDateTime(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        const mm = String(date.getMonth() + 1).padStart(2, '0');
        const dd = String(date.getDate()).padStart(2, '0');
        const hh = String(date.getHours()).padStart(2, '0');
        const min = String(date.getMinutes()).padStart(2, '0');
        return `${mm}-${dd} ${hh}:${min}`;
    }


    return (
        <div className={"tab-content"}>
            {/* 🎛️ 상단 필터 영역 */}
            <div className="filter-container">
                {/* 🔽 시즌 선택 */}
                <div className="season-select-container">
                    <label className="season-label">
                        시즌 선택
                        {selectedSeasonId && (() => {
                            const selected = seasonOptions.find(s => s.id === selectedSeasonId);
                            if (!selected) return null;
                            return (
                                <span className="season-period">
                                    &nbsp;({selected.startAt.slice(5, 10)} ~ {selected.endAt.slice(5, 10)})
                                </span>
                            );
                        })()}
                    </label>
                    <select
                        className="season-select"
                        value={selectedSeasonId || ''}
                        onChange={(e) => setSelectedSeasonId(Number(e.target.value))}
                    >
                        {seasonOptions.map(season => (
                            <option key={season.id} value={season.id}>
                                {season.name}
                            </option>
                        ))}
                    </select>
                </div>

                {/* 🔘 주문 종류 필터 (전체 / 매수 / 매도) */}
                <div className="type-select-container">
                    <label className="season-label">종류</label>
                    <div className="type-buttons">
                        {['전체', '매수', '매도'].map(type => (
                            <button
                                key={type}
                                className={`filter-button ${selectedType === type ? 'active' : ''}`}
                                onClick={() => setSelectedType(type)}
                            >
                                {type}
                            </button>
                        ))}
                    </div>
                </div>

                {/* 🔍 코인명 검색 입력 */}
                <div className="coin-select-container">
                    <label className="season-label">코인 검색</label>
                    <input
                        type="text"
                        className="coin-search-input"
                        value={searchKeyword}
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        placeholder="예: 비트코인, BTC"
                    />
                </div>
            </div>

            {/* 📋 주문 내역 테이블 */}
            <div className="holdings-list">
                <div className="holdings-table">
                    {/* 테이블 헤더 */}
                    <div className="table-header">
                        <div className="col">코인명</div>
                        <div className="col">거래수량</div>
                        <div className="col">거래단가&nbsp;<small>(KRW)</small></div>
                        <div className="col">거래금액&nbsp;<small>(KRW)</small></div>
                        <div className="col align-right">체결시간</div>
                        <div className="col align-right">주문시간</div>
                    </div>

                    {/* 테이블 바디 */}
                    <div className="table-body">
                        {filteredOrders.length === 0 ? (
                            <div className="table-row trade-no-data">표시할 주문이 없습니다.</div>
                        ) : (
                            filteredOrders.map((order, idx) => {
                                const symbol = order.symbol ?? '';
                                const quantity = Number(order.amount ?? 0);
                                const unitPrice = Number(order.tradePrice ?? 0);
                                const totalPrice = quantity * unitPrice;

                                return (
                                    <div
                                        key={idx}
                                        className={`table-row ${
                                            selectedType === '전체'
                                                ? order.orderType === 'BUY'
                                                    ? 'row-buy'
                                                    : order.orderType === 'SELL'
                                                        ? 'row-sell'
                                                        : ''
                                                : ''
                                        }`}
                                    >
                                        <div className="col coin-info">
                                            <div className="coin-name">{order.coinName}</div>
                                            <div className="coin-symbol">{symbol}</div>
                                        </div>
                                        <div className="col">{formatNumber(quantity, 10)}</div>

                                        {/* 거래단가 */}
                                        <div className={`col price-cell ${
                                            order.orderType === 'BUY' ? 'sell' : order.orderType === 'SELL' ? 'buy' : ''
                                        }`}>
                                            {unitPrice > 0 ? formatNumber(unitPrice) : '-'}
                                        </div>

                                        {/* 거래금액 */}
                                        <div className={`col price-cell ${
                                            order.orderType === 'BUY' ? 'sell' : order.orderType === 'SELL' ? 'buy' : ''
                                        }`}>
                                            {totalPrice > 0 ? formatNumber(totalPrice) : '-'}
                                        </div>

                                        {/* 체결시간 */}
                                        <div className="col align-right">
                                            {formatDateTime(order.updatedAt)}
                                        </div>

                                        {/* 주문시간 */}
                                        <div className="col align-right">
                                            {formatDateTime(order.createdAt)}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
