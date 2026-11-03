import {Link} from 'react-router-dom';
import Button from '../ui/Button.jsx';

/** 로그인 전 첫 화면 소개 */
export default function Hero({seasonName}) {
    return (
        <div className="flex flex-col gap-4 md:gap-5">
            {seasonName && (
                <span className="self-start rounded-full bg-brand-soft px-3 py-1.5 text-[13px] font-semibold text-brand-fg">
                    {seasonName} 진행 중
                </span>
            )}
            <h1 className="text-[32px] leading-[1.2] font-extrabold tracking-[-0.03em] md:text-[44px]">
                가상 자금으로<br/>실전처럼 코인 투자
            </h1>
            <p className="text-[15px] leading-relaxed text-fg-2 md:text-[17px]">
                업비트 실시간 시세로 사고팔고, 2주 시즌마다 수익률로 순위를 겨뤄요.
                시즌이 끝나면 AI가 내 매매를 돌아봐 줍니다.
            </p>
            <div className="flex flex-wrap gap-3 pt-1">
                <Button as={Link} to="/login" size="lg" className="h-[52px] px-6 text-base">지금 시작하기</Button>
                <Button as={Link} to="/trade" variant="secondary" size="lg" className="h-[52px] px-6 text-base font-semibold">거래소 둘러보기</Button>
            </div>
        </div>
    );
}
