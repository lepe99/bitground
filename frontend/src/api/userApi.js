import api from './axiosConfig';

//사용자 정보 업데이트 닉네임,이메일,이미지
export const updateUserInfo = async (name, email, imageFile) => {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('email', email);
    if (imageFile) {
        formData.append('image', imageFile);
    }

    const response = await api.put('/users/me', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        withCredentials: true,
    });

    return response.data;
};

//사용자 탈퇴 (isDeleted -> 1)
export const softDeleteUser = async () => {
    const response = await api.delete('/users/me',{
        withCredentials: true,
    });
    return response.data;
}