import {Link, useSearchParams} from "react-router-dom";
import {useAuth} from "../auth/useAuth.js";
import Card from "../components/ui/Card.jsx";
import ProfileCard from "../components/mypage/ProfileCard.jsx";
import SeasonReport from "../components/mypage/SeasonReport.jsx";
import ProfileEditForm from "../components/mypage/ProfileEditForm.jsx";
import AdminPanel from "../components/admin/AdminPanel.jsx";
import NotificationSettings from "../components/mypage/NotificationSettings.jsx";

const MENU = [
    {tab: "report", label: "투자 정보"},
    {tab: "profile", label: "프로필 수정"},
    {tab: "notifications", label: "알림 설정"},
];
// 관리자 계정에만 보인다 (권한은 서버가 확인)
const ADMIN_MENU = {tab: "admin", label: "관리자"};

// 모바일은 하단 탭에 없는 화면을 여기서 연다
const MOBILE_LINKS = [
    {to: "/investments", label: "투자내역", desc: "보유 자산 · 거래내역 · 미체결 주문"},
    {to: "/trends", label: "최신동향", desc: "코인 뉴스와 AI 시장 분석"},
    {to: "/notice", label: "공지사항", desc: "시즌 일정과 서비스 소식"},
    {to: "/mypage?tab=notifications", label: "알림 설정", desc: "받을 알림 종류 · 실시간 팝업"},
];

const Chevron = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-fg-3" aria-hidden="true"><path d="M9 6l6 6-6 6"/></svg>
);

/** 마이페이지: 프로필 · 시즌 리포트(AI 리뷰) · 프로필 수정 · (관리자) 관리자 페이지. 모바일은 '내 정보' 탭 */
export default function Mypage() {
    const {user, logout} = useAuth();
    const [searchParams] = useSearchParams();
    const isAdmin = user?.role === "ROLE_ADMIN";
    const menu = isAdmin ? [...MENU, ADMIN_MENU] : MENU;
    const requested = searchParams.get("tab");
    const tab = menu.some((m) => m.tab === requested) ? requested : "report";

    return (
        <div className="mx-auto grid max-w-[1280px] items-start gap-4 px-5 pt-4 pb-10 md:grid-cols-[220px_minmax(0,1fr)] md:gap-6 md:px-8 md:pt-8 md:pb-14">
            <nav aria-label="마이페이지 메뉴" className="hidden flex-col gap-0.5 rounded-[20px] border border-line bg-surface p-2.5 md:flex">
                {menu.map((m) => (
                    <Link
                        key={m.tab}
                        to={m.tab === "report" ? "/mypage" : `/mypage?tab=${m.tab}`}
                        aria-current={tab === m.tab ? "page" : undefined}
                        className={`flex h-11 items-center rounded-xl px-3.5 text-[15px] ${tab === m.tab ? "bg-surface-2 font-bold" : "font-medium text-fg-2 hover:text-fg"}`}
                    >
                        {m.label}
                    </Link>
                ))}
            </nav>

            <div className="flex min-w-0 flex-col gap-4 md:gap-5">
                <h1 className="text-xl font-extrabold md:sr-only">내 정보</h1>
                {tab === "admin" ? (
                    <>
                        <Link to="/mypage" className="flex items-center gap-1 text-sm font-semibold text-fg-2 md:hidden">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>
                            내 정보로
                        </Link>
                        <AdminPanel/>
                    </>
                ) : tab === "profile" || tab === "notifications" ? (
                    <>
                        <Link to="/mypage" className="flex items-center gap-1 text-sm font-semibold text-fg-2 md:hidden">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>
                            내 정보로
                        </Link>
                        {tab === "profile" ? <ProfileEditForm/> : <NotificationSettings/>}
                    </>
                ) : (
                    <>
                        <ProfileCard/>
                        <Card as="nav" aria-label="바로가기" className="flex flex-col px-5 py-1 md:hidden">
                            {(isAdmin ? [...MOBILE_LINKS, {to: "/mypage?tab=admin", label: "관리자", desc: "운영 현황 · 사용자 · 커뮤니티 · 공지"}] : MOBILE_LINKS).map((l) => (
                                <Link key={l.to} to={l.to} className="flex items-center gap-3 border-b border-line py-3.5 last:border-b-0">
                                    <span className="flex flex-1 flex-col">
                                        <span className="text-[15px] font-semibold">{l.label}</span>
                                        <span className="text-xs text-fg-3">{l.desc}</span>
                                    </span>
                                    <Chevron/>
                                </Link>
                            ))}
                        </Card>
                        <SeasonReport/>
                        <button type="button" onClick={logout} className="h-12 rounded-2xl bg-surface text-[15px] font-semibold text-fg-2 md:hidden">
                            로그아웃
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}
