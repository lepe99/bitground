import {useEffect, useRef} from 'react';
import {useTheme} from '../../theme/ThemeContext.js';

/**
 * TradingView 업비트 차트. 테마가 바뀌면 위젯을 다시 그린다.
 * @param height 픽셀 높이
 * @param compact 모바일용: 도구 모음·지표를 숨긴다
 */
export default function TradingViewWidget({market, height = 400, compact = false}) {
    const containerRef = useRef(null);
    const {theme} = useTheme();

    useEffect(() => {
        const container = containerRef.current;
        if (!market || !container) return;
        // KRW-BTC → UPBIT:BTCKRW
        const [base, asset] = market.split('-');
        container.innerHTML = '<div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>';

        const script = document.createElement('script');
        script.src = 'https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js';
        script.type = 'text/javascript';
        script.async = true;
        script.innerHTML = JSON.stringify({
            autosize: true,
            symbol: `UPBIT:${asset}${base}`,
            interval: '60',
            timezone: 'Asia/Seoul',
            theme,
            backgroundColor: theme === 'dark' ? '#131720' : '#FFFFFF',
            style: '1',
            locale: 'kr',
            hide_legend: true,
            hide_top_toolbar: compact,
            hide_side_toolbar: true,
            // range를 주면 TradingView가 그 기간에 맞는 봉을 골라 interval(1시간)을 무시한다 (모바일 '1D'가 짧은 봉으로 바뀌던 원인)
            allow_symbol_change: false,
            studies: compact ? [] : ['STD;SMA'],
            support_host: 'https://www.tradingview.com',
        });
        container.appendChild(script);
        return () => {
            container.innerHTML = '';
        };
    }, [market, theme, compact]);

    // 위젯 스크립트가 컨테이너의 style을 height:100%로 덮어쓰므로 높이는 바깥 상자에 둔다
    return (
        <div className="w-full" style={{height}}>
            <div ref={containerRef} className="tradingview-widget-container size-full"/>
        </div>
    );
}
