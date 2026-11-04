import {Link, NavLink} from "react-router-dom";
import {useAuth} from "../auth/useAuth";
import Logo from "../components/ui/Logo.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Button from "../components/ui/Button.jsx";
import ThemeToggle from "../components/ui/ThemeToggle.jsx";
import NotificationMenu from "./NotificationMenu.jsx";

const NAV_ITEMS = [
    {to: "/trade", label: "거래소"},
    {to: "/investments", label: "투자내역"},
    {to: "/trends", label: "최신동향"},
    {to: "/community", label: "커뮤니티"},
    {to: "/rank", label: "랭킹"},
    {to: "/notice", label: "공지사항"},
];

/**
 * 상단 헤더. PC는 메뉴 전체를, 모바일은 로고와 알림만 보여주고 메뉴는 하단 탭(BottomNav)이 맡는다.
 */
export default function Header() {
    const {isLoggedIn, user, logout} = useAuth();

    return (
        <header className="sticky top-0 z-40 border-b border-line bg-surface">
            <div className="flex h-14 items-center gap-6 px-5 md:gap-6 lg:gap-10">
                <Link to="/" aria-label="Bitground 홈" className="text-fg">
                    <Logo size={26}/>
                </Link>

                <nav aria-label="주 메뉴" className="hidden min-w-0 flex-1 items-center gap-4 md:flex lg:gap-7">
                    {NAV_ITEMS.map((item) => (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            className={({isActive}) =>
                                `whitespace-nowrap text-sm ${isActive ? "font-bold text-fg" : "font-medium text-fg-2 hover:text-fg"}`
                            }
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </nav>

                <div className="ml-auto flex items-center gap-2 md:ml-0 md:gap-3">
                    <ThemeToggle/>
                    {isLoggedIn ? (
                        <>
                            <NotificationMenu/>
                            <Link to="/mypage" aria-label="마이페이지" className="hidden rounded-full md:flex">
                                <Avatar src={user?.profileImage} name={user?.name} tier={user?.currentTier ?? user?.tier} size="sm" badge={false}/>
                            </Link>
                            <Button variant="ghost" size="sm" onClick={logout} className="max-md:hidden">
                                로그아웃
                            </Button>
                        </>
                    ) : (
                        <Button as={Link} to="/login" variant="inverse" size="sm" className="px-4">
                            로그인
                        </Button>
                    )}
                </div>
            </div>
        </header>
    );
}
