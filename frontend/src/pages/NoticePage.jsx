import {useEffect, useState} from "react";
import {useAuth} from "../auth/useAuth.js";
import {fetchNotices} from "../api/noticeApi.js";
import Card from "../components/ui/Card.jsx";
import {Link} from "react-router-dom";
import Pagination from "../components/ui/Pagination.jsx";
import RichContent from "../components/ui/RichContent.jsx";

const PAGE_SIZE = 10;

/** 공지사항: 검색 · 펼쳐 읽기. 등록·수정·삭제는 관리자 페이지(마이페이지 > 관리자 > 공지)에서 */
export default function NoticePage() {
    const {user} = useAuth();
    const isAdmin = user?.role === "ROLE_ADMIN";
    const [query, setQuery] = useState("");
    const [keyword, setKeyword] = useState("");
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [openId, setOpenId] = useState(null);

    // 입력을 멈추고 0.3초 뒤에 검색한다
    useEffect(() => {
        const timer = setTimeout(() => {
            setKeyword(query.trim());
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [query]);

    useEffect(() => {
        let alive = true;
        fetchNotices({page, size: PAGE_SIZE, keyword})
            .then((res) => alive && setData(res))
            .catch((error) => {
                console.error("공지사항 조회 실패:", error);
                if (alive) setData({content: [], totalPages: 0});
            });
        return () => {
            alive = false;
        };
    }, [page, keyword]);

    return (
        <div className="mx-auto flex max-w-[880px] flex-col gap-4 px-5 pt-4 pb-10 md:gap-5 md:pt-8 md:pb-14">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-xl font-extrabold md:text-[28px]">공지사항</h1>
                {isAdmin && (
                    <Link to="/mypage?tab=admin&section=notices" className="inline-flex h-9 items-center rounded-[10px] border border-line px-3.5 text-[13px] font-semibold text-fg-2 hover:bg-surface-2">
                        공지 관리
                    </Link>
                )}
            </div>

            <>
                    <label className="flex h-11 items-center gap-2 rounded-xl bg-surface px-4 text-fg-3 md:border md:border-line">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
                        <span className="sr-only">공지 검색</span>
                        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="제목·내용 검색" className="min-w-0 flex-1 text-[15px] text-fg outline-none placeholder:text-fg-3"/>
                    </label>

                    <Card className="overflow-hidden">
                        {data === null && <p className="px-5 py-12 text-center text-sm text-fg-3">불러오는 중…</p>}
                        {data?.content.length === 0 && <p className="px-5 py-12 text-center text-sm text-fg-3">{keyword ? "검색 결과가 없어요." : "등록된 공지가 없어요."}</p>}
                        <ul>
                            {data?.content.map((n) => {
                                const open = openId === n.id;
                                return (
                                    <li key={n.id} className="border-b border-line last:border-b-0">
                                        <div className="flex items-center gap-2 pr-3">
                                            <button
                                                type="button"
                                                onClick={() => setOpenId(open ? null : n.id)}
                                                aria-expanded={open}
                                                className="flex min-w-0 flex-1 flex-col gap-1 px-5 py-4 text-left"
                                            >
                                                <span className="text-[15px] font-semibold">{n.title}</span>
                                                <span className="num text-xs text-fg-3">{n.writer} · {n.createdAt?.slice(0, 10).replaceAll("-", ".")}</span>
                                            </button>
                                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={`shrink-0 text-fg-3 transition ${open ? "rotate-180" : ""}`} aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                                        </div>
                                        {open && <RichContent html={n.content} className="border-t border-line bg-surface-2/50 px-5 py-5"/>}
                                    </li>
                                );
                            })}
                        </ul>
                    </Card>
                    <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={setPage}/>
            </>
        </div>
    );
}
