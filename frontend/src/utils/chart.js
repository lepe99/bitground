/**
 * 값 배열을 SVG polyline 좌표 문자열로 바꾼다. 여러 선을 같은 축으로 그리려면 min·max를 넘긴다.
 * null·NaN 값은 건너뛴다 (x 위치는 원래 순번을 유지)
 */
export function toPoints(values, width, height, min, max) {
    const nums = (values ?? []).filter((v) => Number.isFinite(v));
    if (nums.length < 2) return null;
    const lo = min ?? Math.min(...nums);
    const hi = max ?? Math.max(...nums);
    const pad = 3;
    const step = width / (values.length - 1);
    return values
        .map((v, i) => (Number.isFinite(v) ? `${(i * step).toFixed(1)},${(height - pad - ((v - lo) / (hi - lo || 1)) * (height - pad * 2)).toFixed(1)}` : null))
        .filter(Boolean)
        .join(' ');
}
