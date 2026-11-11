import {NavLink} from "react-router-dom";

const TABS = [
    {to: "/", label: "홈", end: true, icon: "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"},
    {to: "/trade", label: "거래", icon: "M4 17l5-6 4 3 7-8M15 6h5v5"},
    {to: "/rank", label: "랭킹", icon: "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM17 5h3v2a3 3 0 0 1-3 3M7 5H4v2a3 3 0 0 0 3 3"},
    {to: "/community", label: "커뮤니티", icon: "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"},
    {to: "/mypage", label: "내 정보", icon: "M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"},
];

/** 모바일 하단 탭 (768px 미만에서만 보인다) */
export default function BottomNav() {
    return (
        <nav
            aria-label="하단 메뉴"
            className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
        >
            {TABS.map((tab) => (
                <NavLink
                    key={tab.to}
                    to={tab.to}
                    end={tab.end}
                    className={({isActive}) =>
                        `flex h-14 flex-col items-center justify-center gap-1 text-[11px] ${isActive ? "font-bold text-fg" : "font-medium text-fg-3"}`
                    }
                >
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d={tab.icon}/>
                    </svg>
                    {tab.label}
                </NavLink>
            ))}
        </nav>
    );
}
