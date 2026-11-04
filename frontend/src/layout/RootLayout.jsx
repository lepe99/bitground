import Header from "./Header.jsx";
import BottomNav from "./BottomNav.jsx";
import Footer from "./Footer.jsx";
import {Outlet, useLocation} from "react-router-dom";

// 모바일 거래소·투자내역은 앱처럼 한 화면을 꽉 채우므로 푸터를 숨긴다
const APP_LIKE_PATHS = ["/trade", "/investments"];

export default function RootLayout() {
    const {pathname} = useLocation();
    const appLike = APP_LIKE_PATHS.includes(pathname);

    return (
        <div className="flex min-h-dvh flex-col bg-bg text-fg">
            <Header/>
            <main className="flex-1">
                <Outlet/>
            </main>
            <Footer className={appLike ? "max-md:hidden" : ""}/>
            {/* 모바일은 하단 탭 높이만큼 아래 여백을 둔다 */}
            <div className="h-[calc(72px+env(safe-area-inset-bottom))] md:hidden" aria-hidden="true"/>
            <BottomNav/>
        </div>
    );
}
