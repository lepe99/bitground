import React, {useEffect, useState} from "react";
import '../../styles/mypage/MyInfo.css'
import {useAuth} from "../../auth/useAuth.js";
import {softDeleteUser, updateUserInfo} from "../../api/userApi.js";
import {FiUpload} from "react-icons/fi";
import {useToast} from "../Toast.jsx";

export default function MyInfo() {
    const {user, loading} = useAuth();
    const {errorAlert} = useToast();

    const [nickname, setNickname] = useState('');
    const [preview, setPreview] = useState('');
    const [email, setEmail] = useState('');
    const [file, setFile] = useState(null); // 실제 업로드용 파일 추가

    useEffect(() => {
        if (user) {
            setNickname(user.name || '');
            setPreview(user.profileImage || '');
            setEmail(user.email || '');
        }
    }, [user]);

    const handleImageChange = (e) => {
        const selectedFile = e.target.files?.[0];
        if (selectedFile) {
            const previewURL = URL.createObjectURL(selectedFile);
            setPreview(previewURL); // 미리보기용
            setFile(selectedFile); // 업로드용
        }
    };

    const handleSubmit = async () => {
        if (nickname.length < 2 || nickname.length > 8) {
            errorAlert('닉네임은 2자 이상 8자 이하로 입력해주세요.');
            return;
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            errorAlert('올바른 이메일 형식이 아닙니다.');
            return;
        }

        try {
            const response = await updateUserInfo(nickname, email, file);
            alert('수정이 완료되었습니다!');
            console.log('서버 응답:', response);
            window.location.reload();
        } catch (error) {
            console.error('업데이트 실패:', error);
            errorAlert('수정 중 오류가 발생했습니다.');
        }
    };

    const handleDeleteAccount = async () => {
        const confirm = window.confirm('정말 탈퇴하시겠습니까?');
        if (!confirm) return;

        try {
            await softDeleteUser();
            errorAlert('탈퇴가 완료되었습니다.');
            //로그아웃 처리
            window.dispatchEvent(new Event('forceLogout'));
        } catch (error) {
            console.error('탈퇴실패:', error);
            errorAlert('탈퇴 중 문제가 발생했습니다.');
        }
    }

    if (loading) return <div>로딩 중...</div>;

    return (
        <div className="info-edit-wrapper">
            <div className={"info-edit-title"}>
                <div>Profile</div>
                <span>프로필 수정하기</span>
            </div>
            <div className="info-edit-container">
                <div className={"info-edit-basic"}>
                    <div>기본 정보</div>
                    <span>내용을 모두 입력해 주세요.</span>
                </div>
                <div className="form-group">
                    <label>닉네임</label>&nbsp;
                    <span>(닉네임은 2자 이상 8자 이하로 입력해주세요.)</span>
                    <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)}/>
                </div>

                <div className="form-group">
                    <label>이메일</label>
                    <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}/>
                </div>

                <div className="form-group">
                    <label>로그인 경로</label>
                    <input type="text" value={user.provider} disabled/>
                </div>
                <div className={"info-edit-basic"}>
                    <div>프로필 이미지</div>
                    <span>5MB 이하의 이미지파일만 업로드됩니다.</span>
                </div>
                <div className="form-group form-edit-file">
                    <input type="file" id="fileInput" accept="image/*" onChange={handleImageChange}
                    style={{display: "none"}} />
                    {preview && <img src={preview} alt="미리보기" className="preview-image"/>}
                    {/* 업로드 버튼 역할 */}
                    <label htmlFor="fileInput" className="custom-upload-btn">
                        <FiUpload className="upload-icon" />
                        &nbsp; Upload
                    </label>
                </div>

                <div className="edit-btns">
                    <button className="edit-submit-btn" onClick={handleSubmit}>수정하기</button>
                    <button className="delete-account-btn" onClick={handleDeleteAccount}>회원탈퇴</button>
                </div>
            </div>
        </div>
    );
}