import {toPoints} from '../../utils/chart.js';

/**
 * 작은 꺾은선 그래프 (SVG). 값 배열을 너비·높이에 맞춰 그린다.
 * @param className 선 색 클래스 (예: 'text-up') — stroke는 currentColor
 * @param dashed 점선 (비교용 어제 값 등)
 */
export default function Sparkline({values, width = 120, height = 32, className = '', strokeWidth = 2, dashed = false, min, max}) {
    const points = toPoints(values, width, height, min, max);
    if (!points) return <svg width={width} height={height} aria-hidden="true"/>;

    return (
        <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true" className={className}>
            <polyline
                points={points}
                fill="none"
                stroke="currentColor"
                strokeWidth={strokeWidth}
                strokeLinejoin="round"
                strokeLinecap="round"
                strokeDasharray={dashed ? '4 4' : undefined}
                vectorEffect="non-scaling-stroke"
            />
        </svg>
    );
}
