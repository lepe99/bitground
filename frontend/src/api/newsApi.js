import api from "./axiosConfig.js";

const BASE_URL = '/news';

/** 뉴스 검색. page는 1부터 */
export const fetchNews = async (keyword, start = 1, display = 5) => {
    try {
        // 네이버 검색의 start는 '몇 번째 기사부터'라서 쪽 번호를 기사 위치로 바꾼다 (2쪽 → 6번째부터)
        const response = await api.get(BASE_URL, {
            params: { keyword, start: (start - 1) * display + 1, display },
        });
        const data = response.data;

        // totalPage 직접 계산하기
        const totalResults = 100; // 네이버 뉴스 API는 최대 100개까지 제공
        const totalPage = Math.ceil(totalResults / display);

        return {
            newsData: data,
            totalPage,
        };
    } catch (error) {
        console.error('Error fetching news:', error);
        return { newsData: [], totalPage: 1 };
    }
};