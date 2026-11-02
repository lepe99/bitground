import api from './axiosConfig';

export const fetchPublicRanking = async () => {
    const res = await api.get('/public/ranking');
    return res.data;
};