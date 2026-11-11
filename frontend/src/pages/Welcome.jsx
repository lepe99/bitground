import {Navigate, useNavigate} from 'react-router-dom';
import Button from '../components/ui/Button.jsx';
import Logo from '../components/ui/Logo.jsx';
import ProfileImagePicker from '../components/profile/ProfileImagePicker.jsx';
import {useProfileForm} from '../components/profile/useProfileForm.js';
import {useToast} from '../components/toastContext.js';

const PROVIDERS = {google: 'Google', naver: '네이버', kakao: '카카오'};
const INPUT = 'h-12 rounded-xl border border-line bg-surface px-4 text-[15px] outline-none placeholder:text-fg-3 focus:border-fg-3';

/**
 * 처음 가입한 사용자만 들어오는 프로필 확인 화면.
 * 소셜 로그인에서 받아온 사진·닉네임·이메일을 기본값으로 보여주고, 고치거나 그대로 시작한다
 */
export default function Welcome() {
    const navigate = useNavigate();
    const {infoAlert} = useToast();
    const form = useProfileForm();
    const {user, name, setName, email, setEmail, preview, saving, pickImage, resetImage, save} = form;

    // 이미 마친 사용자는 들어올 수 없다
    if (user?.onboarded) return <Navigate to="/" replace/>;

    const submit = async (e) => {
        e.preventDefault();
        if (!(await save())) return;
        infoAlert(`${name.trim()}님, 환영합니다!`);
        navigate('/', {replace: true});
    };

    const provider = PROVIDERS[user?.provider];
    const nameLength = name.trim().length;

    return (
        <div className="flex items-center justify-center px-5 py-10 md:py-16">
            <form onSubmit={submit} className="flex w-full max-w-[440px] flex-col gap-6 rounded-3xl bg-surface p-7 md:border md:border-line md:p-9">
                <div className="flex flex-col items-center gap-3 text-center">
                    <Logo size={40} withText={false}/>
                    <h1 className="text-2xl font-extrabold tracking-[-0.02em]">프로필을 확인해 주세요</h1>
                    <p className="text-sm leading-relaxed text-fg-2">
                        {provider ? `${provider}에서 받아온 정보로 채워 두었어요.` : '서비스에서 쓸 프로필이에요.'}
                        <br/>바꾸고 싶은 것만 고치고 시작하세요.
                    </p>
                </div>

                <ProfileImagePicker preview={preview} tier={user?.tier} onPick={pickImage} onReset={resetImage} className="justify-center"/>

                <label className="flex flex-col gap-1.5">
                    <span className="flex items-baseline justify-between text-sm font-semibold">
                        닉네임
                        <span className={`num text-xs font-normal ${nameLength > 8 ? 'text-up' : 'text-fg-3'}`}>{nameLength}/8</span>
                    </span>
                    <input value={name} onChange={(e) => setName(e.target.value)} placeholder="2~8자" className={INPUT} autoComplete="nickname"/>
                    {nameLength > 8 && <span className="text-xs text-up">8자 이하로 줄여 주세요.</span>}
                </label>

                <label className="flex flex-col gap-1.5">
                    <span className="text-sm font-semibold">이메일 <span className="font-normal text-fg-3">(선택)</span></span>
                    <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="공지·알림을 받을 이메일" className={INPUT} autoComplete="email"/>
                </label>

                <Button type="submit" disabled={saving} className="h-12 rounded-xl">
                    {saving ? '저장 중…' : '시작하기'}
                </Button>
            </form>
        </div>
    );
}
