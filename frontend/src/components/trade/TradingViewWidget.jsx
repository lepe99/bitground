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
            range: compact ? '1D' : '7D',
            allow_symbol_change: false,
            studies: compact ? [] : ['STD;SMA'],
            support_host: 'https://www.tradingview.com',
        });
        container.appendChild(script);
        return () => {
            container.innerHTML = '';
        };
    }, [market, theme, compact]);

    return <div ref={containerRef} className="tradingview-widget-container w-full" style={{height}}/>;
}
