import React, { useEffect, useRef } from 'react';
import '../../styles/rank/TierInfoTooltip.css';

import bronze from '../../assets/images/bronze.webp';
import silver from '../../assets/images/silver.webp';
import gold from '../../assets/images/gold.webp';
import platinum from '../../assets/images/platinum.webp';
import diamond from '../../assets/images/diamond.webp';
import master from '../../assets/images/master.webp';
import grandmaster from '../../assets/images/grandmaster.webp';

const tierLogoImageMap = {
    1: bronze,
    2: silver,
    3: gold,
    4: platinum,
    5: diamond,
    6: master,
    7: grandmaster,
};

const tierNameMap = {
    1: 'Bronze',
    2: 'Silver',
    3: 'Gold',
    4: 'Platinum',
    5: 'Diamond',
    6: 'Master',
    7: 'Grandmaster',
};

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
                        <img src={tierLogoImageMap[tier]} alt={tierNameMap[tier]} className="tier-img" />
                        <span>{tierNameMap[tier]}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}