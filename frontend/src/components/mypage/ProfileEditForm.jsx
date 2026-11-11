import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import ProfileImagePicker from '../profile/ProfileImagePicker.jsx';
import {useProfileForm} from '../profile/useProfileForm.js';
import {useToast} from '../toastContext.js';
import {softDeleteUser} from '../../api/userApi.js';
import {getErrorMessage} from '../../api/apiError.js';

const PROVIDERS = {google: '구글', naver: '네이버', kakao: '카카오'};

function Field({label, hint, children}) {
    return (
        <label className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold">{label} {hint && <span className="font-normal text-fg-3">{hint}</span>}</span>
            {children}
        </label>
    );
}

const INPUT = 'h-12 rounded-xl border border-line bg-surface px-4 text-[15px] outline-none placeholder:text-fg-3 focus:border-fg-3 disabled:bg-surface-2 disabled:text-fg-3';

/** 닉네임·이메일·프로필 사진 수정(사진 삭제 시 기본 프로필 사진), 회원 탈퇴 */
export default function ProfileEditForm() {
    const {infoAlert, errorAlert} = useToast();
    const {user, name, setName, email, setEmail, preview, saving, pickImage, resetImage, save} = useProfileForm();

    const submit = async (e) => {
        e.preventDefault();
        if (await save()) infoAlert('프로필을 수정했어요.');
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
        <Card as="form" onSubmit={submit} className="flex flex-col gap-5 p-5 md:max-w-[560px] md:p-7">
            <h2 className="text-lg font-extrabold">프로필 수정</h2>

            <ProfileImagePicker preview={preview} tier={user?.currentTier ?? user?.tier} onPick={pickImage} onReset={resetImage}/>

            <Field label="닉네임" hint="2~8자">
                <input value={name} onChange={(e) => setName(e.target.value)} maxLength={8} className={INPUT}/>
            </Field>
            <Field label="이메일" hint="(선택)">
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="공지·알림을 받을 이메일" className={INPUT}/>
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
