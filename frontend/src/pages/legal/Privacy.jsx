import LegalDocument from '../../components/legal/LegalDocument.jsx';
import {SITE} from '../../constants/site.js';

// 실제로 받는 정보는 백엔드 OAuth 설정(scope)과 User 엔티티 기준. 바뀌면 이 문서도 함께 고친다
const SECTIONS = [
    {
        title: '수집하는 개인정보',
        body: (
            <>
                <p>{SITE.name}는 별도 회원가입 없이 소셜 계정으로 로그인하며, 로그인할 때 아래 정보를 해당 서비스로부터 받습니다.</p>
                <table>
                    <thead>
                        <tr><th>로그인 방식</th><th>받는 정보</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>Google</td><td>이름, 이메일 주소, 프로필 사진, Google 계정 식별자</td></tr>
                        <tr><td>네이버</td><td>이름(별명), 프로필 사진, 네이버 계정 식별자</td></tr>
                        <tr><td>카카오</td><td>닉네임, 프로필 사진, 카카오 계정 식별자</td></tr>
                    </tbody>
                </table>
                <p>서비스를 이용하는 동안 다음 정보가 만들어져 저장됩니다.</p>
                <ul>
                    <li>가상 자산·주문·거래 기록, 시즌 순위와 티어, 관심 코인</li>
                    <li>직접 올린 프로필 사진, 작성한 게시글·댓글, 받은 알림</li>
                    <li>접속 일시, IP 주소, 브라우저 정보 등 서비스 운영·보안을 위한 접속 기록</li>
                </ul>
                <p>{SITE.name}는 비밀번호, 전화번호, 주민등록번호, 실제 금융 정보를 받지 않습니다.</p>
            </>
        ),
    },
    {
        title: '이용 목적',
        body: (
            <ul>
                <li>로그인과 회원 식별, 프로필(이름·사진) 표시</li>
                <li>모의투자, 시즌 랭킹, 커뮤니티, 알림 등 서비스 제공</li>
                <li>시즌 거래 내역을 바탕으로 한 AI 투자 회고 제공</li>
                <li>부정 이용 방지, 장애 대응, 보안 사고 조사</li>
            </ul>
        ),
    },
    {
        title: 'Google 사용자 데이터 처리',
        body: (
            <>
                <p>
                    Google 로그인으로 받은 정보(이름, 이메일 주소, 프로필 사진, 계정 식별자)는 <strong>로그인과 프로필 표시에만</strong> 사용합니다.
                    이 정보를 광고에 쓰거나, 판매하거나, 위 목적 밖에서 다른 곳에 넘기지 않습니다.
                </p>
                <p>
                    {SITE.name}가 Google API로 받은 정보를 사용하고 다른 앱으로 전송하는 방식은 제한적 사용 요구사항을 포함한{' '}
                    <a href="https://developers.google.com/terms/api-services-user-data-policy" target="_blank" rel="noreferrer" className="underline underline-offset-2">
                        Google API 서비스 사용자 데이터 정책
                    </a>
                    을 따릅니다.
                </p>
            </>
        ),
    },
    {
        title: '보유 기간과 파기',
        body: (
            <>
                <p>개인정보는 회원 자격이 유지되는 동안 보관합니다.</p>
                <ul>
                    <li>회원 탈퇴 시 이메일 주소와 소셜 계정 식별자를 즉시 삭제하고, 로그인 토큰을 모두 무효화합니다.</li>
                    <li>
                        이름(닉네임), 프로필 사진, 작성한 게시글·댓글, 지난 시즌 거래·순위 기록은 다른 이용자의 기록과 시즌 결과가 깨지지 않도록 남을 수 있습니다.
                        이 정보의 삭제를 원하면 탈퇴 전에 직접 지우거나 아래 문의처로 요청해 주세요.
                    </li>
                    <li>법령에 따라 보관해야 하는 정보는 그 기간 동안만 따로 보관한 뒤 파기합니다.</li>
                </ul>
            </>
        ),
    },
    {
        title: '제3자 제공과 외부 서비스',
        body: (
            <>
                <p>{SITE.name}는 이용자의 개인정보를 제3자에게 판매하거나 제공하지 않습니다. 서비스 운영을 위해 다음 외부 서비스를 씁니다.</p>
                <ul>
                    <li><strong>Google · 네이버 · 카카오</strong>: 소셜 로그인 인증</li>
                    <li><strong>Google Gemini API</strong>: AI 투자 회고 생성. 이름·이메일 등 회원을 알아볼 수 있는 정보 없이 시즌 거래 통계(수익률, 거래 횟수, 코인별 손익 등)만 보냅니다.</li>
                    <li><strong>업비트(Upbit) 공개 API</strong>: 코인 시세. 브라우저가 공개 시세를 직접 받아오며 개인정보는 보내지 않습니다.</li>
                    <li><strong>클라우드 서버·저장소</strong>: 서비스 데이터와 프로필 사진 보관</li>
                </ul>
            </>
        ),
    },
    {
        title: '쿠키와 브라우저 저장소',
        body: (
            <ul>
                <li>로그인 상태 유지를 위해 자바스크립트로 읽을 수 없는(HttpOnly) 인증 쿠키를 씁니다.</li>
                <li>밝은/어두운 화면 설정은 브라우저 저장소(localStorage)에 저장합니다.</li>
                <li>광고나 방문 분석을 위한 추적 쿠키는 쓰지 않습니다.</li>
            </ul>
        ),
    },
    {
        title: '이용자의 권리',
        body: (
            <ul>
                <li>마이페이지에서 이름과 프로필 사진을 언제든 바꿀 수 있습니다.</li>
                <li>마이페이지에서 언제든 회원 탈퇴할 수 있습니다.</li>
                <li>내 개인정보의 열람·정정·삭제·처리 정지를 아래 문의처로 요청할 수 있으며, 지체 없이 처리합니다.</li>
                <li>각 소셜 서비스의 계정 설정에서 {SITE.name}와의 연결을 끊을 수도 있습니다.</li>
            </ul>
        ),
    },
    {
        title: '안전성 확보 조치',
        body: (
            <ul>
                <li>모든 통신은 HTTPS로 암호화합니다.</li>
                <li>로그인 토큰은 서명해 위·변조를 막고, 탈퇴·로그아웃 시 무효화합니다.</li>
                <li>개인정보에 접근할 수 있는 인원과 권한을 최소한으로 둡니다.</li>
            </ul>
        ),
    },
    {
        title: '만 14세 미만 아동',
        body: <p>{SITE.name}는 만 14세 미만 아동을 대상으로 하지 않으며, 만 14세 미만 아동의 개인정보를 알면서 받지 않습니다.</p>,
    },
    {
        title: '개인정보 보호책임자와 문의',
        body: (
            <>
                <p>개인정보와 관련한 문의, 불만, 피해 구제 요청은 아래로 연락해 주세요.</p>
                <ul>
                    <li>개인정보 보호책임자: {SITE.operator}</li>
                    <li>이메일: {SITE.contactEmail}</li>
                </ul>
                <p>개인정보 침해에 대한 신고·상담은 개인정보침해신고센터(privacy.kisa.or.kr, 국번 없이 118)에도 할 수 있습니다.</p>
            </>
        ),
    },
    {
        title: '방침의 변경',
        body: <p>이 방침을 바꾸면 시행 7일 전에 공지사항으로 알립니다. 이용자에게 불리하게 바뀌는 경우에는 30일 전에 알립니다.</p>,
    },
];

export default function Privacy() {
    return (
        <LegalDocument
            title="개인정보처리방침"
            intro={`${SITE.name}(이하 '서비스')는 이용자의 개인정보를 소중히 다루며, 어떤 정보를 왜 받고 어떻게 보관하는지 아래와 같이 알립니다.`}
            sections={SECTIONS}
        />
    );
}
