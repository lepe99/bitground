import React from 'react';
import '../../styles/trade/coinDetail.css';
import {VscHeart, VscHeartFilled} from "react-icons/vsc";

export default function CoinDetail ({market, data, marketName,favoriteMarkets = [], onToggleFav}) {
    if (!market) {
        return <div className="coin-detail">코인을 선택해주세요</div>;
    }

    const {price, changeAmt, changeRate, high, low} = data || {};
    const isFav = favoriteMarkets.includes(market);

    return (
        <div className="coin-detail">
            {/* 제목 & 즐겨찾기 */}
            <div className="coin-header">
                <h2 className="coin-detail-name">
                    {marketName} &nbsp;<span className="coin-code">{market}</span>
                </h2>
                <button className={"btn-heart"}
                    onClick={e => {
                        e.stopPropagation();
                        onToggleFav(market);
                    }}
                >
                    {isFav
                        ? <VscHeartFilled className="favorite-btn filled" />
                        : <VscHeart       className="favorite-btn" />}
                </button>
            </div>
            <div className={"coin-detail-content"}>
                {/* 가격 & 변동 */}
                <div className="coin-price-section">
                    <div className={`coin-price ${changeAmt >= 0 ? 'up' : 'down'}`}>
                        {price != null ? price.toLocaleString(undefined, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 8
                        }) : '—'}
                        <span className={`unit ${changeAmt >= 0 ? 'up' : 'down'}`}>KRW</span>
                    </div>
                    <div className="coin-change">
                  <span className={'change-rate'}>
                    {changeRate != null ? (changeRate * 100).toFixed(2) + '%' : '—'}
                  </span>
                        <span className="change-rate">
                        {changeAmt > 0 ? '▲' :'▼'}&nbsp;{changeAmt != null ? changeAmt.toLocaleString(undefined, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 8
                        }) : '—'}
                    </span>
                    </div>
                </div>

                {/* 24H 고가·저가 */}
                <div className="coin-stats">
                    <div className="stat">
                        <div className="label">고가 <span className="sub-label">KRW&nbsp;(24H)</span></div>
                        <div className="value high">
                            {high != null ? high.toLocaleString(undefined, {
                                minimumFractionDigits: 0,
                                maximumFractionDigits: 8
                            }) : '—'}
                        </div>
                    </div>
                    <div className="stat">
                        <div className="label">저가 <span className="sub-label">KRW&nbsp;(24H)</span></div>
                        <div className="value low">
                            {low != null ? low.toLocaleString(undefined, {
                                minimumFractionDigits: 0,
                                maximumFractionDigits: 8
                            }) : '—'}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};
