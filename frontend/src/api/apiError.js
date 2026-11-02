// 백엔드 에러 응답(RFC 9457 Problem Details) 해석 유틸
// { type, title, status, detail, instance, code, timestamp, errors? }

const DEFAULT_MESSAGE = '요청 처리 중 오류가 발생했습니다.';

// 사용자에게 보여줄 메시지: 검증 오류가 있으면 첫 필드 메시지, 없으면 detail
export const getErrorMessage = (error, fallback = DEFAULT_MESSAGE) => {
    const problem = error?.response?.data;
    if (problem?.errors?.length) {
        return problem.errors[0].message;
    }
    return problem?.detail ?? fallback;
};

// 분기용 에러 코드 (예: INSUFFICIENT_CASH, AI_INSIGHT_NOT_FOUND)
export const getErrorCode = (error) => error?.response?.data?.code;
