import api from "./axiosConfig.js";

const BASE_URL = '/news';

export const fetchNews = async (keyword, start = 1, display = 5) => { // display 기본값을 4로 변경
    try {
        const response = await api.get(BASE_URL, {
            params: { keyword, start, display },
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