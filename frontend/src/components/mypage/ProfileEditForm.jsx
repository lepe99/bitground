import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import Avatar from '../ui/Avatar.jsx';
import {useAuth} from '../../auth/useAuth.js';
import {useToast} from '../toastContext.js';
import {softDeleteUser, updateUserInfo} from '../../api/userApi.js';
import {getErrorMessage} from '../../api/apiError.js';

const PROVIDERS = {google: '구글', naver: '네이버', kakao: '카카오'};
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function Field({label, hint, children}) {
    return (
        <label className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold">{label} {hint && <span className="font-normal text-fg-3">{hint}</span>}</span>
            {children}
        </label>
    );
}

const INPUT = 'h-12 rounded-xl border border-line bg-surface px-4 text-[15px] outline-none focus:border-fg-3 disabled:bg-surface-2 disabled:text-fg-3';

/** 닉네임·이메일·프로필 사진 수정, 회원 탈퇴 */
export default function ProfileEditForm() {
    const {user} = useAuth();
    const {infoAlert, errorAlert} = useToast();
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!user) return;
        setName(user.name ?? '');
        setEmail(user.email ?? '');
        setPreview(user.profileImage ?? '');
    }, [user]);

    // 미리보기 주소는 다른 사진을 고르거나 화면을 떠날 때 해제한다
    useEffect(() => () => {
        if (preview.startsWith('blob:')) URL.revokeObjectURL(preview);
    }, [preview]);

    const pickImage = (e) => {
        const selected = e.target.files?.[0];
        if (!selected) return;
        if (selected.size > MAX_IMAGE_BYTES) {
            errorAlert('5MB 이하의 이미지만 올릴 수 있어요.');
            return;
        }
        setFile(selected);
        setPreview(URL.createObjectURL(selected));
    };

    const save = async (e) => {
        e.preventDefault();
        if (name.trim().length < 2 || name.trim().length > 8) return errorAlert('닉네임은 2자 이상 8자 이하로 입력해주세요.');
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return errorAlert('올바른 이메일 형식이 아닙니다.');
        setSaving(true);
        try {
            await updateUserInfo(name.trim(), email, file);
            infoAlert('프로필을 수정했어요.');
            // 헤더 등 다른 화면의 사용자 정보도 새로 읽도록 다시 불러온다
            setTimeout(() => window.location.reload(), 600);
        } catch (error) {
            errorAlert(getErrorMessage(error, '수정 중 오류가 발생했습니다.'));
        } finally {
            setSaving(false);
        }
    };

    const withdraw = async () => {
        if (!window.confirm('정말 탈퇴할까요? 거래 기록과 순위를 더 볼 수 없어요.')) return;
        try {
            await softDeleteUser();
            infoAlert('탈퇴가 완료되었습니다.');
            window.dispatchEvent(new Event('forceLogout'));
        } catch (error) {
            errorAlert(getErrorMessage(error, '탈퇴 중 문제가 발생했습니다.'));
        }
    };

    return (
        <Card as="form" onSubmit={save} className="flex flex-col gap-5 p-5 md:max-w-[560px] md:p-7">
            <h2 className="text-lg font-extrabold">프로필 수정</h2>

            <div className="flex items-center gap-4">
                <Avatar src={preview} name={name} tier={user?.tier} size="xl" badge={false}/>
                <div className="flex flex-col gap-1.5">
                    <label className="inline-flex h-9 w-fit cursor-pointer items-center rounded-[10px] border border-line px-3.5 text-[13px] font-semibold hover:bg-surface-2">
                        사진 바꾸기
                        <input type="file" accept="image/*" onChange={pickImage} className="sr-only"/>
                    </label>
                    <span className="text-xs text-fg-3">5MB 이하 이미지</span>
                </div>
            </div>

            <Field label="닉네임" hint="2~8자">
                <input value={name} onChange={(e) => setName(e.target.value)} maxLength={8} className={INPUT}/>
            </Field>
            <Field label="이메일">
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={INPUT}/>
            </Field>
            <Field label="로그인 경로">
                <input value={PROVIDERS[user?.provider] ?? user?.provider ?? ''} disabled className={INPUT}/>
            </Field>

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                <Button type="submit" disabled={saving} className="min-w-28">{saving ? '저장 중…' : '저장'}</Button>
                <button type="button" onClick={withdraw} className="text-[13px] text-fg-3 underline underline-offset-2 hover:text-up">회원 탈퇴</button>
            </div>
        </Card>
    );
}
