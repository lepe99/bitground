import {useEffect, useState} from 'react';
import {useAuth} from '../../auth/useAuth.js';
import {useToast} from '../toastContext.js';
import {updateUserInfo} from '../../api/userApi.js';
import {getErrorMessage} from '../../api/apiError.js';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * 프로필(사진·닉네임·이메일) 입력 상태. 처음 가입 화면(/welcome)과 마이페이지 수정이 함께 쓴다
 * - 사진: 새로 고르면 미리보기, '기본 사진으로'를 누르면 지울 사진으로 표시 (저장할 때 반영)
 * - 이메일은 비워 둘 수 있다 (카카오처럼 이메일을 주지 않는 로그인)
 * @returns save(): 성공하면 true
 */
export function useProfileForm() {
    const {user, checkAuthState} = useAuth();
    const {errorAlert} = useToast();
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState('');
    const [removeImage, setRemoveImage] = useState(false);
    const [saving, setSaving] = useState(false);

    // 서버의 현재 값을 기본값으로 채운다 (소셜 로그인에서 받아온 값 포함)
    useEffect(() => {
        if (!user) return;
        setName(user.name ?? '');
        setEmail(user.email ?? '');
        setPreview(user.profileImage ?? '');
        setFile(null);
        setRemoveImage(false);
    }, [user]);

    // 미리보기 주소는 다른 사진을 고르거나 화면을 떠날 때 해제한다
    useEffect(() => () => {
        if (preview.startsWith('blob:')) URL.revokeObjectURL(preview);
    }, [preview]);

    const pickImage = (selected) => {
        if (!selected) return;
        if (!selected.type.startsWith('image/')) return errorAlert('이미지 파일만 올릴 수 있어요.');
        if (selected.size > MAX_IMAGE_BYTES) return errorAlert('5MB 이하의 이미지만 올릴 수 있어요.');
        setFile(selected);
        setPreview(URL.createObjectURL(selected));
        setRemoveImage(false);
    };

    const resetImage = () => {
        setFile(null);
        setPreview('');
        setRemoveImage(true);
    };

    const save = async () => {
        const trimmedName = name.trim();
        const trimmedEmail = email.trim();
        if (trimmedName.length < 2 || trimmedName.length > 8) {
            errorAlert('닉네임은 2자 이상 8자 이하로 입력해주세요.');
            return false;
        }
        if (trimmedEmail && !EMAIL.test(trimmedEmail)) {
            errorAlert('올바른 이메일 형식이 아닙니다.');
            return false;
        }
        setSaving(true);
        try {
            await updateUserInfo({name: trimmedName, email: trimmedEmail, imageFile: file, removeImage});
            // 헤더 등 다른 화면도 새 정보를 쓰도록 사용자 정보를 다시 읽는다
            await checkAuthState();
            return true;
        } catch (error) {
            errorAlert(getErrorMessage(error, '저장 중 오류가 발생했습니다.'));
            return false;
        } finally {
            setSaving(false);
        }
    };

    return {
        user, name, setName, email, setEmail, preview, saving,
        hasImage: Boolean(preview), pickImage, resetImage, save,
    };
}
