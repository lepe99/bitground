import Header from "./Header.jsx";
import BottomNav from "./BottomNav.jsx";
import {Outlet} from "react-router-dom";

export default function RootLayout() {
    return (
        <div className="min-h-dvh bg-bg text-fg">
            <Header/>
            {/* 모바일은 하단 탭 높이만큼 아래 여백을 둔다 */}
            <main className="pb-[calc(72px+env(safe-area-inset-bottom))] md:pb-0">
                <Outlet/>
            </main>
            <BottomNav/>
        </div>
    );
}
