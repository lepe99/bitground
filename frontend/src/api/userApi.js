import api from './axiosConfig';

/**
 * 프로필 수정 (닉네임, 이메일, 사진). 처음 가입 후 확인 화면도 이걸로 마친다
 * @param email 비우면 이메일 없음
 * @param imageFile 새 사진 (없으면 사진 그대로)
 * @param removeImage true면 사진을 지워 기본 프로필 사진으로
 */
export const updateUserInfo = async ({name, email, imageFile, removeImage = false}) => {
    const formData = new FormData();
    formData.append('name', name);
    formData.append('email', email ?? '');
    if (imageFile) formData.append('image', imageFile);
    if (removeImage) formData.append('removeImage', 'true');

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
