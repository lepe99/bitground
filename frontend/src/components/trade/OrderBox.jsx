import React, {useEffect, useRef, useState} from 'react';
import '../../styles/trade/OrderBox.css';
import api from '../../api/axiosConfig.js';
import {useToast} from "../Toast.jsx";

export default function OrderBox({selectedMarket, tickerMap, onOrderPlaced, cash, holdings,onTradeTabChange}) {
    const [tradeTab, setTradeTab] = useState('BUY');
    const [amount, setAmount] = useState('');
    const [orderType, setOrderType] = useState('BUY');
    const [tradeType, setTradeType] = useState('market');
    const [price, setPrice] = useState('');
    const [totalPrice, setTotalPrice] = useState('');
    const [loading, setLoading] = useState(false);
    const inputRef = useRef(null);
    const amountRef = useRef(null);
    const [fontSize, setFontSize] = useState('1.8rem');
    const { infoAlert, errorAlert } = useToast();

    const currentPrice = tickerMap[selectedMarket]?.price ?? 0;

    const formattedHolding = holdings.toLocaleString(undefined, {
        minimumFractionDigits: 0,
        maximumFractionDigits: 10
    });
    const currency = selectedMarket.split("-")[0];
    const displaySymbol = selectedMarket.split('-')[1];

    const displayValue =
        tradeTab === 'BUY'
            ? `${cash.toLocaleString()} ${currency}`
            : `${formattedHolding} ${displaySymbol}`;

    useEffect(() => {
        const numericLength = displayValue.replace(/[^0-9]/g, '').length;
        if (numericLength < 11) setFontSize('1.8rem');
        else if (numericLength < 13) setFontSize('1.6rem');
        else if (numericLength < 17) setFontSize('1.4rem');
        else setFontSize('1.2rem');
    }, [displayValue]);

    useEffect(() => {
        if (tradeType === 'reserve' && price === '') {
            setPrice(formatNumber(currentPrice));
        }
    }, [tradeType]);

    useEffect(() => {
        if (tradeType === 'reserve') {
            const rawPrice = parseFloat(price.replace(/,/g, ''));
            const rawAmount = parseFloat(amount.replace(/,/g, ''));
            if (isNaN(rawPrice) || isNaN(rawAmount)) {
                setTotalPrice('');
                return;
            }

            const rawTotal = rawPrice * rawAmount;
            const total = orderType === 'BUY'
                ? Math.ceil(rawTotal)  // 매수는 올림
                : Math.floor(rawTotal); // 매도는 내림

            setTotalPrice(total.toLocaleString());
        }
    }, [price, amount, tradeType, orderType]);

    useEffect(() => {
        setPrice(formatNumber(currentPrice))
        setAmount('');
        setTotalPrice('');
    }, [tradeTab, tradeType]);

    useEffect(() => {
        setTradeTab('BUY');
        setOrderType('BUY');
        setTradeType('market');
        setTotalPrice('');
    }, [selectedMarket]);

    const formatNumber = (value) => {
        const raw = typeof value === 'number' ? value : parseFloat(value.toString().replace(/,/g, ''));
        if (isNaN(raw)) return '';
        if (Number.isInteger(raw)) return raw.toLocaleString();
        let str = raw.toLocaleString('en-US', {maximumFractionDigits: 10});
        return str.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
    };

    const handlePriceChange = (e) => {
        const input = e.target;
        const raw = input.value;
        const cleaned = raw.replace(/,/g, '');

        // 숫자 입력 외에는 무시
        if (!/^(\d+\.?\d*|\.\d*)?$/.test(cleaned)) return;

        const caret = input.selectionStart;

        setPrice(cleaned);

        // 커서 위치 복원
        requestAnimationFrame(() => {
            if (input.setSelectionRange) {
                input.setSelectionRange(caret, caret);
            }
        });
    };

    const handlePriceBlur = () => {
        setPrice(formatNumber(price)); // 포맷은 blur 이벤트에서만 적용
    };


    const handleTotalPriceChange = (e) => {
        const input = e.target;
        const raw = input.value.replace(/,/g, '');

        if (!/^\d*$/.test(raw)) return;

        const caret = input.selectionStart;

        setTotalPrice(raw);  // 쉼표 없이 저장

        requestAnimationFrame(() => {
            input.setSelectionRange(caret, caret);
        });
    };

    const handleTotalPriceBlur = () => {
        setTotalPrice(formatNumber(totalPrice));
    };


    const handleAmountChange = (e) => {
        const input = e.target;
        const raw = input.value.replace(/,/g, '');

        if (!/^(\d+\.?\d*|\.\d*)?$/.test(raw)) return;

        const caret = input.selectionStart;

        setAmount(raw);

        requestAnimationFrame(() => {
            input.setSelectionRange(caret, caret);
        });
    };

    const handleAmountBlur = () => {
        setAmount(formatNumber(amount));
    };

    const handlePercentClick = (percent) => {
        if (tradeTab === 'BUY') {
            const budget = cash * percent;
            setTotalPrice(formatNumber(tradeType === 'BUY' ? Math.ceil(budget) : Math.floor(budget)));
            if (tradeType === 'market') {
                const qty = currentPrice > 0 ? budget / currentPrice : 0;
                setAmount(formatNumber(qty));
            } else {
                const reservePrice = parseFloat(price.replace(/,/g, ''));
                const qty = reservePrice > 0 ? budget / reservePrice: 0;
                setAmount(formatNumber(qty));
            }
        } else if (tradeTab === 'SELL') {
            const qty = holdings * percent;
            setAmount(formatNumber(qty));
        }
    };

    const handlePlaceOrder = async () => {
        const rawAmount = parseFloat(amount.replace(/,/g, ''));
        const rawPrice = parseFloat(price.replace(/,/g, ''));
        const rawTotalPrice = parseFloat(totalPrice.toString().replace(/,/g, ''));

        setLoading(true);
        try {
            const isBuy = orderType === 'BUY';
            const isReserve = tradeType === 'reserve';

            if (isReserve) {
                if (isNaN(rawAmount) || rawAmount <= 0) {
                    return errorAlert('예약 주문 수량을 입력해주세요.');
                }
                if (isNaN(rawPrice) || rawPrice <= 0) {
                    return errorAlert('예약 가격을 입력해주세요.');
                }
                const estTotal = rawAmount * rawPrice;
                if (isBuy) {
                    if (cash < estTotal) {
                        const adjustedAmount = Math.floor((cash / rawPrice) * 1e10) / 1e10;

                        setAmount(adjustedAmount.toString());
                        errorAlert('잔액이 부족합니다.');
                        return infoAlert('보유 현금에 맞게 수량이 자동 조정되었습니다.');
                    }
                }
                if (!isBuy) {
                    if (holdings < rawAmount) {
                        setAmount(holdings.toString());
                        errorAlert('보유 수량이 부족합니다.');
                        return infoAlert("보유 수량에 맞게 수량이 자동 조정되었습니다.")
                    }
                }
            } else {
                if (isBuy) {
                    if (isNaN(rawTotalPrice) || rawTotalPrice <= 0) {
                        return errorAlert('주문 총액을 입력해주세요.');
                    }
                    if (cash < rawTotalPrice) {
                        return errorAlert('잔액이 부족합니다.');
                    }
                } else {
                    if (isNaN(rawAmount) || rawAmount <= 0) {
                        return errorAlert('매도 수량을 입력해주세요.');
                    }
                }
            }

            const payload = {
                symbol: selectedMarket,
                orderType,
                ...(isReserve
                    ? {
                        amount: rawAmount,
                        reservePrice: rawPrice
                    }
                    : isBuy
                        ? { totalPrice: rawTotalPrice }
                        : { amount: rawAmount })
            };

            const url = isReserve ? '/trade/reserve' : '/trade';
            const response = await api.post(url, payload);

            if (!isReserve) {
                if (isBuy) {
                    infoAlert(`${response.data.amount}개 매수 주문이 체결되었습니다.`);
                } else {
                    const total = Math.floor(response.data.tradePrice * response.data.amount);
                    infoAlert(`${formatNumber(total)} ${currency}에 매도 주문이 체결되었습니다.`);
                }
            } else {
                infoAlert('예약 주문이 접수되었습니다.');
            }

            onOrderPlaced?.(response.data);
            setAmount('');
            setTotalPrice('');
        }  finally {
            setLoading(false);
        }
    };

    //부모에 sell/buy 값 전달
    useEffect(() => {
        if (typeof onTradeTabChange === 'function') {
            onTradeTabChange(tradeTab);
        }
    }, [tradeTab]);

    return (
        <div className="trade-form-wrapper">
            <div className="trade-tabs">
                <div className="trade-sellbuy-tab">
                    <span className={tradeTab === 'BUY' ? 'active' : ''} onClick={() => { setTradeTab('BUY'); setOrderType('BUY'); }}>매수</span>
                    <span className={tradeTab === 'SELL' ? 'active' : ''} onClick={() => { setTradeTab('SELL'); setOrderType('SELL'); }}>매도</span>
                </div>
            </div>

            <div className="order-radio-section">
                <div className="order-label">주문유형</div>
                <div className="radio-group">
                    <label>
                        <input type="radio" name="tradeType" value="market" checked={tradeType === 'market'} onChange={(e) => setTradeType(e.target.value)} /> 시장가
                    </label>
                    <label>
                        <input type="radio" name="tradeType" value="reserve" checked={tradeType === 'reserve'} onChange={(e) => setTradeType(e.target.value)} /> 예약주문
                    </label>
                </div>
            </div>

            <div className={tradeTab === 'BUY' ? 'buy-form' : 'sell-form'}>
                <div className="buy-section">
                    <div className="label">주문가능</div>
                    <input className="buy-money" ref={amountRef} style={{fontSize}} type="text" value={tradeTab === 'BUY' ? `${cash.toLocaleString()} ${currency}` : `${formattedHolding} ${displaySymbol}`} readOnly />
                </div>

                {tradeType === 'reserve' && (
                    <div className="buy-section">
                        <div className="label">{tradeTab === 'BUY' ? '매수가격' : '매도가격'} <span>({currency})</span></div>
                        <input
                            className={"buy-price-insert"}
                            ref={inputRef}
                            value={price}
                            onChange={handlePriceChange}
                            onBlur={handlePriceBlur}
                        />
                    </div>
                )}

                <div className="percent-buttons">
                    {[0.1, 0.25, 0.5, 1].map((p, i) => (
                        <button key={i} type="button" onClick={() => handlePercentClick(p)}>{p * 100}%</button>
                    ))}
                </div>

                {(tradeTab === 'SELL' || tradeType === 'reserve') && (
                    <div className="buy-section">
                        <div className="label">주문수량 <span>({displaySymbol})</span></div>
                        <div className="buy-count">
                            <input className="buy-count-insert" type="text" value={amount} onChange={handleAmountChange} onBlur={handleAmountBlur} />
                        </div>
                    </div>
                )}

                {(tradeTab === 'BUY' || tradeType === 'reserve') && (
                    <div className="buy-section">
                        <div className="label">주문총액 <span>({currency})</span></div>
                        <input
                            className="buy-total-cost"
                            type="text"
                            value={totalPrice}
                            onChange={handleTotalPriceChange}
                            onBlur={handleTotalPriceBlur}
                            readOnly={(!(tradeTab === 'BUY' && tradeType === 'market'))}
                        />
                    </div>
                )}

                <button className={tradeTab === 'BUY' ? 'buy-btn' : 'sell-btn'} onClick={handlePlaceOrder} disabled={loading}>
                    {loading ? '주문 처리 중…' : tradeTab === 'BUY' ? '매수' : '매도'}
                </button>
            </div>
        </div>
    );
}
