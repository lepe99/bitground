import {Link} from "react-router-dom";
import Logo from "../components/ui/Logo.jsx";
import {SITE} from "../constants/site.js";

const LINKS = [
    {to: "/terms", label: "서비스 이용약관"},
    {to: "/privacy", label: "개인정보처리방침", strong: true},
    {to: "/notice", label: "공지사항"},
];

/** 사이트 하단: 약관·개인정보처리방침 링크, 서비스 성격 고지, 시세 출처, 문의처 */
export default function Footer({className = ""}) {
    return (
        <footer className={`border-t border-line bg-surface ${className}`}>
            <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-5 py-8 text-xs text-fg-3 md:px-8">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <Logo size={22}/>
                    <nav aria-label="사이트 정보" className="flex flex-wrap gap-x-4 gap-y-1">
                        {LINKS.map((l) => (
                            <Link key={l.to} to={l.to} className={`hover:text-fg ${l.strong ? "font-bold text-fg-2" : "text-fg-2"}`}>
                                {l.label}
                            </Link>
                        ))}
                    </nav>
                </div>
                <div className="flex flex-col gap-1 leading-relaxed">
                    <p>{SITE.name}는 가상 자금으로 하는 모의투자 서비스입니다. 실제 자산을 거래하지 않으며, 서비스의 정보는 투자 권유가 아닙니다.</p>
                    <p>시세 정보: 업비트(Upbit) 공개 API · 운영: {SITE.operator} · 문의: {SITE.contactEmail}</p>
                </div>
                <p>© 2026 {SITE.name}</p>
            </div>
        </footer>
    );
}
