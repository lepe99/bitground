// src/api/fetchAiAdvice.js

import api from './axiosConfig'; // axiosConfig가 올바르게 임포트되었다고 가정합니다.

/**
 * 특정 시즌에 대한 AI 투자 조언을 백엔드에서 조회합니다.
 * 백엔드 컨트롤러(/ai-advice?seasonId={seasonId})는 이 요청을 받아
 * 조언이 없으면 생성하고, 있으면 기존 조언을 반환합니다.
 * User ID는 백엔드에서 인증된 사용자 정보를 통해 자동으로 가져오므로, 여기서는 seasonId만 전달합니다.
 * @param {number} seasonId - 조언을 조회하거나 생성할 시즌의 ID
 * @returns {Promise<object|null>} AI 조언 데이터 (score, advice 등) 또는 존재하지 않거나 오류 발생 시 null 반환
 */
export const fetchAiAdvice = async (seasonId) => {
    if (!seasonId) {
        console.warn("AI 조언 조회 요청: Season ID가 유효하지 않아 조회를 건너뜁니다.");
        return null; // 유효하지 않은 seasonId일 경우 조회하지 않고 null 반환
    }

    try {
        // 백엔드 AiAdviceController의 @RequestMapping("/ai-advice") 및 @GetMapping에 맞춰 경로 수정
        // 쿼리 파라미터로 seasonId를 전달합니다.
        const response = await api.get(`/mypage/ai-advice?seasonId=${seasonId}`);
        console.log(`AI 조언 조회/생성 요청 성공 (Season ID: ${seasonId}):`, response.data);
        return response.data;
    } catch (error) {
        // 404 Not Found는 조언이 아직 생성되지 않았을 수 있음을 의미하거나,
        // 사용자 또는 시즌 정보가 유효하지 않을 수 있습니다.
        if (error.response) {
            if (error.response.status === 404) {
                console.info(`AI 조언을 찾을 수 없습니다 (Season ID: ${seasonId}). 사용자 또는 시즌 문제일 수 있습니다.`, error.message);
                return null; // 조언이 없는 경우
            } else if (error.response.status === 400) {
                // 예를 들어, 시즌 상태가 COMPLETED가 아닐 때 백엔드에서 400 Bad Request를 반환할 수 있습니다.
                console.error(`AI 조언 요청 오류 (Season ID: ${seasonId}, Status: ${error.response.status}):`, error.response.data || error.message);
                throw new Error(`AI 조언 요청 오류: ${error.response.data?.message || error.message}`);
            } else if (error.response.status === 500) {
                console.error(`AI 조언 요청 중 서버 내부 오류 (Season ID: ${seasonId}):`, error.response.data || error.message);
                throw new Error(`서버 오류 발생: ${error.response.data?.message || '알 수 없는 서버 오류'}`);
            }
        }
        console.error(`AI 조언 조회 요청 실패 (Season ID: ${seasonId}):`, error);
        throw error; // 다른 유형의 오류는 throw
    }
};

// 기존의 generateAiAdvice 함수는 백엔드 컨트롤러에 맞는 POST 엔드포인트가 없으므로 제거됩니다.
// 백엔드 AiAdviceController는 GET 요청으로 조언 생성/조회 로직을 모두 처리합니다.
