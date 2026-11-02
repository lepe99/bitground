import React, { useEffect, useRef, memo } from 'react';

export default function TradingViewWidget({ market }) {
    const containerRef = useRef(null);

    useEffect(() => {
            if (!market) return;
            // KRW-BTC → ['KRW','BTC'] → UPBIT:BTCKRW
            const [base, asset] = market.split('-');
            const symbol = `UPBIT:${asset}${base}`;
            // 이전에 삽입된 위젯 제거
            containerRef.current.innerHTML = '';
            const script = document.createElement("script");
            script.src = "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js";
            script.type = "text/javascript";
            script.async = true;
            script.innerHTML = JSON.stringify({
                height: '400',
                symbol,
                interval: '60',
                timezone: 'Asia/Seoul',
                theme: 'light',
                style: '1',
                locale: 'kr',
                hide_legend: true,
                range: '7D',
                allow_symbol_change: false,
                studies: ['STD;SMA'],
                support_host: 'https://www.tradingview.com'
            });
        // 컨테이너에 붙이기
        containerRef.current.appendChild(script);
    }, [market]);

    return (
        <div className="tradingview-widget-container" ref={containerRef} style={{ height: "100%", width: "100%" }}>
            <div className="tradingview-widget-container__widget" style={{ height: "calc(100% - 32px)", width: "100%" }}></div>
        </div>
    );
}
