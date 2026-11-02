import React, { useState, useEffect, useMemo, useContext } from 'react';
import { useToast } from "../Toast.jsx"; // 알림 토스트 훅
import api from '../../api/axiosConfig'; // Axios 인스턴스
import { TickerContext } from "../../ticker/TickerProvider"; // 실시간 시세 컨텍스트

export default function PendingOrders() {
    const { infoAlert, errorAlert } = useToast();
    const { tickerMap } = useContext(TickerContext); // 실시간 시세 맵

    // 상태값
    const [pendingOrders, setPendingOrders] = useState([]); // 예약 주문 목록
    const [filter, setFilter] = useState('all'); // 필터 상태
    const [selectedIds, setSelectedIds] = useState([]); // 선택된 주문 ID 목록

    // 예약 주문 목록 불러오기
    const fetchOrders = () => {
        api.get('/orders/reserve')
            .then(res => {
                setPendingOrders(Array.isArray(res.data) ? res.data : []);
                setSelectedIds([]);
            })
            .catch(err => {
                errorAlert('주문 목록 불러오기 실패');
                setPendingOrders([]);
            });
    };

    // 최초 마운트 시 데이터 요청
    useEffect(() => {
        fetchOrders();
    }, []);

    // 주문 목록 필터링 (매수 / 매도 / 전체)
    const filteredOrders = useMemo(() => {
        if (filter === 'all') return pendingOrders;
        return pendingOrders.filter(order => order.orderType?.toLowerCase() === filter);
    }, [filter, pendingOrders]);

    // 주문 클릭 시 선택 토글
    const toggleSelect = (orderId) => {
        setSelectedIds(prev =>
            prev.includes(orderId)
                ? prev.filter(id => id !== orderId)
                : [...prev, orderId]
        );
    };

    // 숫자 포맷 함수
    const formatNumber = (value) => {
        const num = Number(value);
        return isNaN(num) ? '-' : num.toLocaleString();
    };

    // 날짜 포맷 함수
    const formatDate = (value) => {
        if (!value) return '-';
        const date = new Date(value);
        const yy = date.getFullYear();
        const mm = String(date.getMonth() + 1).padStart(2, '0');
        const dd = String(date.getDate()).padStart(2, '0');
        const hh = String(date.getHours()).padStart(2, '0');
        const min = String(date.getMinutes()).padStart(2, '0');
        return `${yy}-${mm}-${dd} ${hh}:${min}`;
    };

    // 선택된 주문 일괄 취소
    const handleCancelSelected = () => {
        if (selectedIds.length === 0) {
            errorAlert('선택된 주문이 없습니다.');
            return;
        }
        if (!window.confirm('선택한 주문을 모두 취소하시겠습니까?')) return;

        Promise.all(selectedIds.map(id => api.delete(`/orders/${id}`)))
            .then(() => {
                infoAlert('주문이 취소되었습니다.');
                fetchOrders();
            })
            .catch(() => {
                errorAlert('주문 취소 실패');
            });
    };

    // 렌더링
    return (
        <div className="holdings-list">
            {/* ───── 필터 & 버튼 영역 ───── */}
            <div className="holdings-header">
                <div className="orders-header">
                    <select
                        className="order-select"
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                    >
                        <option value="all">전체주문</option>
                        <option value="buy">매수주문</option>
                        <option value="sell">매도주문</option>
                    </select>
                    <button className="cancel-selected-btn" onClick={handleCancelSelected}>
                        거래취소
                    </button>
                </div>
            </div>

            {/* ───── 테이블 헤더 ───── */}
            <div className="holdings-table">
                <div className="table-header">
                    <div className="col">코인명</div>
                    <div className="col">주문수량</div>
                    <div className="col">감시가격&nbsp;<small>KRW</small></div>
                    <div className="col">거래가격&nbsp;<small>KRW</small></div>
                    <div className="col">주문시간</div>
                    {/*<div className="col align-right">미체결량</div>*/}
                </div>

                {/* ───── 테이블 바디 ───── */}
                {filteredOrders.length > 0 ? (
                    filteredOrders.map((item, index) => {
                        const symbolKey = item.symbol.includes('KRW-') ? item.symbol : `KRW-${item.symbol}`;
                        const currentPrice = tickerMap?.[symbolKey]?.price ?? 0;
                        const isSelected = selectedIds.includes(item.id);

                        return (
                            <div
                                key={`${item.id}-${index}`}
                                className={`table-row ${isSelected ? 'selected' : ''}`}
                                onClick={() => toggleSelect(item.id)}
                            >
                                {/* 코인명 */}
                                <div className="col coin-info">
                                    <div>
                                        <div className="coin-name">{item.koreanName}</div>
                                        <div className="coin-symbol">{item.symbol}</div>
                                    </div>
                                </div>

                                {/* 주문수량 */}
                                <div className="col">
                                    {formatNumber(item.amount)} <small>{item.symbol}</small>
                                </div>

                                {/* 감시가격 (실시간 시세) */}
                                <div className="col">
                                    {formatNumber(currentPrice)}
                                </div>

                                {/* 거래가격 (예약 가격) */}
                                <div className="col">
                                    {item.reservePrice ? formatNumber(item.reservePrice) : '-'}
                                </div>

                                {/* 주문시간 */}
                                <div className="col">{formatDate(item.createdAt)}</div>

                                {/* 미체결량 */}
                                {/*<div className="col align-right">*/}
                                {/*    {formatNumber(item.amount)} <small>{item.symbol}</small>*/}
                                {/*</div>*/}
                            </div>
                        );
                    })
                ) : (
                    <div className="table-row">
                        <div className="col trade-no-data">예약 주문이 없습니다.</div>
                    </div>
                )}
            </div>
        </div>
    );
}
