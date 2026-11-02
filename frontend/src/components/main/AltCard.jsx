import "../../styles/main/AltCard.css";
import { GrCircleQuestion } from "react-icons/gr";
import { useState, useRef, useEffect } from "react";

export default function altCard({ title, subtitle, value, avgValue, children }) {
    const isUp = value >= avgValue;
    const diffValue = Math.abs(value - avgValue);
    const diffRate = ((diffValue / avgValue) * 100).toFixed(2);

    const mainColor = isUp ? '#E93E2A' : '#2979ff';
    //툴팁용
    const [showTooltip, setShowTooltip] = useState(false);
    const toggleTooltip = () => setShowTooltip(prev => !prev);
    const tooltipRef = useRef();
    useEffect(() => {
        const handleClickOutside = (event) => {
            if (tooltipRef.current && !tooltipRef.current.contains(event.target)) {
                setShowTooltip(false);
            }
        };
        document.addEventListener("mousedown", handleClickOutside);
        return () => {
            document.removeEventListener("mousedown", handleClickOutside);
        };
    }, []);

    return (
        <div className="alt-card">
            <div className="alt-card-content">
                <div className="alt-card-info">
                    <div className="card-tooltip-container" ref={tooltipRef}>
                        <GrCircleQuestion className="card-tooltip-icon" onClick={toggleTooltip} />
                        {showTooltip && (
                            <div className="card-tooltip-box">
                                BitGround Altcoin Index<br/>
                                <span>비트코인을 제외한 모든 국내 상장 코인의 유동시가총액을 사용한<br/> 시가총액 가중방식 인덱스이며 매 정시마다 산출됩니다.</span>
                            </div>
                        )}
                    </div>
                    <div className="alt-card-title">{title}</div>
                    <div className="alt-card-subtitle">{subtitle}</div>
                    <div className="alt-card-value" style={{ color: mainColor }}>
                        {value.toLocaleString()}
                    </div>
                    <div className="alt-card-diff" style={{ color: mainColor }}>
                        {isUp ? '▲' : '▼'} {diffValue.toLocaleString()} ({diffRate}%)
                    </div>
                </div>
                <div className="alt-card-chart">
                    {children}
                </div>
            </div>
        </div>
    );
}