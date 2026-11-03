import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/Toast.jsx';
import Logo from '../components/ui/Logo.jsx';

// 각 서비스의 로그인 버튼 안내에 맞춘 색
const PROVIDERS = [
    {id: 'kakao', label: '카카오로 시작하기', className: 'bg-[#FEE500] text-[#191919] hover:brightness-95'},
    {id: 'naver', label: '네이버로 시작하기', className: 'bg-[#03C75A] text-white hover:brightness-95'},
    {id: 'google', label: 'Google로 시작하기', className: 'border border-line bg-white text-[#1F1F1F] hover:bg-[#F7F8F8]'},
];

export default function Login() {
    const {login} = useAuth();
    const {errorAlert} = useToast();
    const [searchParams] = useSearchParams();
    const errorCode = searchParams.get('error');

    // 로그인 실패 시 서버가 ?error=OAUTH_CANCELLED | OAUTH_FAILED 로 돌려보낸다
    useEffect(() => {
        if (errorCode === 'OAUTH_CANCELLED') errorAlert('로그인이 취소되었습니다.');
        else if (errorCode) errorAlert('로그인 중 오류가 발생했습니다. 다시 시도해주세요.');
    }, [errorCode, errorAlert]);

    return (
        <div className="rd flex min-h-[calc(100dvh-56px-72px)] items-center justify-center px-5 py-10 md:min-h-[calc(100dvh-64px)]">
            <div className="flex w-full max-w-[400px] flex-col gap-7 rounded-3xl bg-surface p-7 md:border md:border-line md:p-9">
                <div className="flex flex-col items-center gap-3 text-center">
                    <Logo size={44} withText={false}/>
                    <h1 className="text-2xl font-extrabold tracking-[-0.02em]">Bitground 시작하기</h1>
                    <p className="text-sm leading-relaxed text-fg-2">가상 자금으로 실전처럼 코인을 사고팔고,<br/>시즌마다 수익률로 순위를 겨뤄요.</p>
                </div>

                <div className="flex flex-col gap-2.5">
                    {PROVIDERS.map((p) => (
                        <button key={p.id} type="button" onClick={() => login(p.id)} className={`h-[52px] rounded-2xl text-[15px] font-bold transition ${p.className}`}>
                            {p.label}
                        </button>
                    ))}
                </div>

                <p className="flex items-center justify-center gap-1.5 text-xs text-fg-3">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
                    접속한 주소가 <span className="font-semibold text-fg-2">{window.location.host}</span>인지 확인하세요
                </p>
            </div>
        </div>
    );
}
