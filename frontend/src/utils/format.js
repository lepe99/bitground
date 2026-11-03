// 화면에 보여줄 숫자 형식

/** 1234567 → '1,234,567' */
export const formatNumber = (value, digits = 0) =>
    Number(value ?? 0).toLocaleString('ko-KR', {minimumFractionDigits: digits, maximumFractionDigits: digits});

/** 원화 금액 (소수점 버림 없이 반올림) */
export const formatKRW = (value) => formatNumber(Math.round(Number(value ?? 0)));

/** 부호 붙인 금액: 1200 → '+1,200', -300 → '-300' */
export const formatSignedKRW = (value) => {
    const n = Math.round(Number(value ?? 0)) || 0; // -0 → 0
    return (n > 0 ? '+' : '') + formatNumber(n);
};

/** 부호 붙인 퍼센트: 1.234 → '+1.23%' */
export const formatPercent = (value, digits = 2) => {
    const n = Number(value ?? 0);
    const text = n.toFixed(digits);
    if (Number(text) === 0) return (0).toFixed(digits) + '%'; // 반올림해서 0이면 부호를 붙이지 않는다 (-0.00% 방지)
    return (n > 0 ? '+' : '') + text + '%';
};

/** 상승·하락 색 클래스 (한국 관례: 상승 빨강, 하락 파랑) */
export const changeColor = (value) => {
    const n = Number(value ?? 0);
    if (n > 0) return 'text-up';
    if (n < 0) return 'text-down';
    return 'text-fg-3';
};

/**
 * 코인 가격. 업비트 원화 호가 단위처럼 가격대에 따라 소수 자릿수를 바꾼다
 * (1,000원 이상 정수, 100원 이상 1자리, 10원 이상 2자리, 1원 이상 3자리, 그 밑 최대 8자리)
 */
export const formatPrice = (value) => {
    if (value == null || Number.isNaN(Number(value))) return '—';
    const n = Number(value);
    const abs = Math.abs(n);
    const digits = abs >= 1000 ? 0 : abs >= 100 ? 1 : abs >= 10 ? 2 : abs >= 1 ? 3 : 8;
    return n.toLocaleString('ko-KR', {maximumFractionDigits: digits});
};

/** 코인 수량 (최대 8자리, 끝의 0은 생략) */
export const formatAmount = (value) =>
    Number(value ?? 0).toLocaleString('ko-KR', {maximumFractionDigits: 8});

/** 큰 원화 금액을 짧게: 4812억, 35백만 */
export const formatCompactKRW = (won) => {
    const n = Number(won ?? 0);
    if (n >= 1e8) return `${formatNumber(Math.round(n / 1e8))}억`;
    if (n >= 1e6) return `${formatNumber(Math.round(n / 1e6))}백만`;
    return formatNumber(Math.round(n));
};
