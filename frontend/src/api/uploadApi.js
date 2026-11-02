import api from './axiosConfig.js';

// 에디터 이미지 업로드. 저장된 이미지 주소를 반환
export const uploadImage = async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.post('/uploads/images', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    });
    return res.data.url;
};
