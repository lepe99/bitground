import React, { useEffect, useRef } from 'react';
import '../../styles/rank/TierInfoTooltip.css';
import {TIER_BADGES, TIER_NAMES} from '../../constants/tiers.js';


export default function TierInfoTooltip({ onClose }) {
    const tooltipRef = useRef();

    // 바깥 클릭 시 닫기
    useEffect(() => {
        function handleClickOutside(e) {
            if (tooltipRef.current && !tooltipRef.current.contains(e.target)) {
                onClose(); // 상위에서 setShowTooltip(false) 호출됨
            }
        }

        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [onClose]);

    return (
        <div className="tier-tooltip" ref={tooltipRef}>
            <h4>🏆 티어 등급 안내</h4>
            <ul>
                {[7, 6, 5, 4, 3, 2, 1].map(tier => (
                    <li key={tier} className="tier-row">
                        <img src={TIER_BADGES[tier]} alt={TIER_NAMES[tier]} className="tier-img" />
                        <span>{TIER_NAMES[tier]}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}