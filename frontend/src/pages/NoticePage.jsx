import {useEffect, useState} from "react";
import {useAuth} from "../auth/useAuth.js";
import {useToast} from "../components/toastContext.js";
import {createNotice, deleteNotice, fetchNotices} from "../api/noticeApi.js";
import {getErrorMessage} from "../api/apiError.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Pagination from "../components/ui/Pagination.jsx";
import RichEditor from "../components/ui/RichEditor.jsx";
import RichContent from "../components/ui/RichContent.jsx";

const PAGE_SIZE = 10;

function NoticeEditor({onDone, onCancel}) {
    const {infoAlert, errorAlert} = useToast();
    const [title, setTitle] = useState("");
    const [content, setContent] = useState("");
    const [saving, setSaving] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        // 에디터는 빈 글도 <p><br></p>로 남기므로 태그를 뺀 글자로 확인한다
        if (!title.trim() || !content.replace(/<[^>]*>/g, "").trim()) return errorAlert("제목과 내용을 입력해주세요.");
        setSaving(true);
        try {
            await createNotice({title: title.trim(), content});
            infoAlert("공지사항을 등록했어요.");
            onDone();
        } catch (err) {
            errorAlert(getErrorMessage(err, "등록하지 못했습니다."));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card as="form" onSubmit={submit} className="flex flex-col gap-4 p-5 md:p-6">
            <h2 className="text-lg font-extrabold">공지 등록</h2>
            <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="제목"
                aria-label="제목"
                className="h-12 rounded-xl border border-line bg-surface px-4 text-base font-semibold outline-none focus:border-fg-3"
            />
            <RichEditor value={content} onChange={setContent} placeholder="공지 내용을 입력하세요"/>
            <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={onCancel}>취소</Button>
                <Button type="submit" disabled={saving}>{saving ? "등록 중…" : "등록"}</Button>
            </div>
        </Card>
    );
}

/** 공지사항: 검색 · 펼쳐 읽기 · (관리자) 등록·삭제 */
export default function NoticePage() {
    const {user} = useAuth();
    const {infoAlert, errorAlert} = useToast();
    const isAdmin = user?.role === "ROLE_ADMIN";
    const [writing, setWriting] = useState(false);
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
        if (writing) return;
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
    }, [page, keyword, writing]);

    const remove = async (id) => {
        if (!window.confirm("이 공지를 삭제할까요?")) return;
        try {
            await deleteNotice(id);
            infoAlert("삭제했어요.");
            setData((d) => ({...d, content: d.content.filter((n) => n.id !== id)}));
        } catch (err) {
            errorAlert(getErrorMessage(err, "삭제하지 못했습니다."));
        }
    };

    return (
        <div className="mx-auto flex max-w-[880px] flex-col gap-4 px-5 pt-4 pb-10 md:gap-5 md:pt-8 md:pb-14">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-xl font-extrabold md:text-[28px]">공지사항</h1>
                {isAdmin && !writing && <Button size="sm" onClick={() => setWriting(true)}>공지 등록</Button>}
            </div>

            {writing ? (
                <NoticeEditor onDone={() => setWriting(false)} onCancel={() => setWriting(false)}/>
            ) : (
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
                                            {isAdmin && (
                                                <button type="button" onClick={() => remove(n.id)} aria-label={`${n.title} 삭제`} className="flex size-9 items-center justify-center rounded-lg text-fg-3 hover:bg-surface-2 hover:text-up">
                                                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
                                                </button>
                                            )}
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
            )}
        </div>
    );
}
