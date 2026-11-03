// 화면에 보여줄 숫자 형식

/** 1234567 → '1,234,567' */
export const formatNumber = (value, digits = 0) =>
    Number(value ?? 0).toLocaleString('ko-KR', {minimumFractionDigits: digits, maximumFractionDigits: digits});

/** 원화 금액 (소수점 버림 없이 반올림) */
export const formatKRW = (value) => formatNumber(Math.round(Number(value ?? 0)));

/** 부호 붙인 금액: 1200 → '+1,200', -300 → '-300' */
export const formatSignedKRW = (value) => {
    const n = Math.round(Number(value ?? 0));
    return (n > 0 ? '+' : '') + formatNumber(n);
};

/** 부호 붙인 퍼센트: 1.234 → '+1.23%' */
export const formatPercent = (value, digits = 2) => {
    const n = Number(value ?? 0);
    return (n > 0 ? '+' : '') + n.toFixed(digits) + '%';
};

/** 상승·하락 색 클래스 (한국 관례: 상승 빨강, 하락 파랑) */
export const changeColor = (value) => {
    const n = Number(value ?? 0);
    if (n > 0) return 'text-up';
    if (n < 0) return 'text-down';
    return 'text-fg-3';
};
