import {useEffect} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext.js';
import Logo from '../components/ui/Logo.jsx';

// 각 서비스의 로그인 버튼 가이드(색·로고·문구)를 따른다
// - 카카오: 배경 #FEE500, 말풍선 심볼 #000, 글자 85% 검정
// - 네이버: 배경 #03C75A, 흰색 N 로고·글자
// - Google: 밝은 테마 흰 바탕·#747775 테두리·#1F1F1F 글자, 어두운 테마 #131314 바탕·#8E918F 테두리·#E3E3E3 글자, 4색 G 로고
const KakaoSymbol = () => (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
        <path fill="#000" d="M9 1.5C4.58 1.5 1 4.3 1 7.75c0 2.22 1.48 4.17 3.71 5.28l-.95 3.47c-.08.3.26.55.53.37l4.14-2.74c.19.02.38.02.57.02 4.42 0 8-2.8 8-6.25S13.42 1.5 9 1.5z"/>
    </svg>
);
const NaverSymbol = () => (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
        <path fill="#fff" d="M10.85 8.56 5.01 0H0v16h5.15V7.44L10.99 16H16V0h-5.15z"/>
    </svg>
);
const GoogleSymbol = () => (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
    </svg>
);

const PROVIDERS = [
    {id: 'kakao', label: '카카오 로그인', Symbol: KakaoSymbol, className: 'bg-[#FEE500] text-black/85 hover:brightness-[0.97]'},
    {id: 'naver', label: '네이버 로그인', Symbol: NaverSymbol, className: 'bg-[#03C75A] text-white hover:brightness-[0.97]'},
    {
        id: 'google',
        label: 'Google 계정으로 로그인',
        Symbol: GoogleSymbol,
        className: 'border border-[#747775] bg-white text-[#1F1F1F] hover:bg-[#F2F2F2] dark:border-[#8E918F] dark:bg-[#131314] dark:text-[#E3E3E3] dark:hover:bg-[#1F1F20]',
    },
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
        <div className="flex items-center justify-center px-5 py-10 md:py-20">
            <div className="flex w-full max-w-[400px] flex-col gap-7 rounded-3xl bg-surface p-7 md:border md:border-line md:p-9">
                <div className="flex flex-col items-center gap-3 text-center">
                    <Logo size={44} withText={false}/>
                    <h1 className="text-2xl font-extrabold tracking-[-0.02em]">Bitground 시작하기</h1>
                    <p className="text-sm leading-relaxed text-fg-2">가상 자금으로 실전처럼 코인을 사고팔고,<br/>시즌마다 수익률로 순위를 겨뤄요.</p>
                </div>

                <div className="flex flex-col gap-2.5">
                    {/* 로고는 왼쪽에 고정하고 문구는 가운데 (각 가이드의 기본 배치) */}
                    {PROVIDERS.map((p) => (
                        <button key={p.id} type="button" onClick={() => login(p.id)} className={`relative flex h-12 items-center justify-center rounded-xl px-12 text-[15px] font-semibold transition ${p.className}`}>
                            <span className="absolute left-4 flex size-5 items-center justify-center"><p.Symbol/></span>
                            {p.label}
                        </button>
                    ))}
                </div>

                <p className="text-center text-xs leading-relaxed text-fg-3">
                    로그인하면 <Link to="/terms" className="font-semibold text-fg-2 underline underline-offset-2">서비스 이용약관</Link>과{' '}
                    <Link to="/privacy" className="font-semibold text-fg-2 underline underline-offset-2">개인정보처리방침</Link>에 동의하는 것으로 봅니다.
                </p>

                <p className="flex items-center justify-center gap-1.5 text-xs text-fg-3">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
                    접속한 주소가 <span className="font-semibold text-fg-2">{window.location.host}</span>인지 확인하세요
                </p>
            </div>
        </div>
    );
}
