import React, {useEffect, useState} from 'react';
import {fetchTodayMarketIndex, fetchYesterdayMarketIndex} from '../../api/marketIndexApi';
import {ComposedChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine, Area} from 'recharts';
import MarketCard from './MarketCard';

export default function MarketChart() {
    const [todayData, setTodayData] = useState([]);
    const [yesterdayData, setYesterdayData] = useState([]);

    useEffect(() => {
        const fetchData = async () => {
            const today = await fetchTodayMarketIndex();
            const yesterday = await fetchYesterdayMarketIndex();
            setTodayData(today);
            setYesterdayData(yesterday);
        };
        fetchData();
    }, []);

    const yesterdayAvg =
        yesterdayData.length > 0
            ? Math.round(yesterdayData.reduce((acc, cur) => acc + cur.marketIndex, 0) / yesterdayData.length)
            : 0;

    const chartData = Array.from({length: 24}, (_, hour) => {
        const hourStr = String(hour).padStart(2, '0') + ':00';
        const todayItem = todayData.find(d => d.hour === hourStr);
        const yesterdayItem = yesterdayData.find(d => d.hour === hourStr);
        return {
            hour: hourStr,
            today: todayItem?.marketIndex ?? null,
            yesterday: yesterdayItem?.marketIndex ?? null,
        };
    });

    const lastToday = todayData.at(-1)?.marketIndex ?? 0;
    const todayColor = lastToday >= yesterdayAvg ? '#E93E2A' : '#2979ff';

    return (
        <MarketCard
            title="BGMI"
            subtitle="BitGround Market Index"
            value={lastToday}
            diffValue={lastToday - (yesterdayData.at(-1)?.marketIndex ?? 0)}
            diffRate={yesterdayData.at(-1)
                ? (((lastToday - yesterdayData.at(-1).marketIndex) / yesterdayData.at(-1).marketIndex) * 100).toFixed(2)
                : 0}
            avgValue={yesterdayAvg} // 기준선 전달
        >
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                height: '120px',
                minWidth: '250px',
                alignItems: 'center'
            }}>
                <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={chartData}>
                        <defs>
                            <linearGradient id="yesterdayGradient" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="0%" stopColor="#999" stopOpacity={0.4}/>
                                <stop offset="100%" stopColor="#999" stopOpacity={0}/>
                            </linearGradient>
                        </defs>
                        <XAxis dataKey="hour" hide/>
                        <YAxis hide domain={['dataMin - 20', 'dataMax + 20']} type="number"/>
                        <Tooltip
                            formatter={(value) => `${value}`}
                            labelFormatter={(label) => `시간: ${label}`}
                        />
                        <ReferenceLine y={yesterdayAvg} stroke="#c8c8c8" strokeDasharray="3 3"/>
                        <Area
                            type="monotone"
                            dataKey="yesterday"
                            fill="url(#yesterdayGradient)"
                            stroke="none"
                            connectNulls
                        />
                        <Line
                            type="monotone"
                            dataKey="today"
                            stroke={todayColor}
                            strokeWidth={2}
                            dot={false}
                            isAnimationActive={false}
                            connectNulls
                        />
                    </ComposedChart>
                </ResponsiveContainer>
                <div>
                    <span style={{color: todayColor}}>● Today</span> &nbsp;
                    <span style={{color: '#c8c8c8'}}>● Yesterday</span>
                </div>
            </div>
        </MarketCard>
    );
}