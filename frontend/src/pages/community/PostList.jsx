import {useEffect, useState} from "react";
import {Link, useSearchParams} from "react-router-dom";
import {fetchPosts} from "../../api/communityApi.js";
import Card from "../../components/ui/Card.jsx";
import Button from "../../components/ui/Button.jsx";
import Avatar from "../../components/ui/Avatar.jsx";
import Pagination from "../../components/ui/Pagination.jsx";
import SegmentedTabs from "../../components/ui/SegmentedTabs.jsx";
import {useAuth} from "../../auth/useAuth.js";
import {
    ANSWER_FILTERS, CATEGORIES, CATEGORY_LABEL, CATEGORY_STYLE, INQUIRY, INQUIRY_TYPE_LABEL, postTime,
} from "../../components/community/post.js";

/** 문의 답변 상태 */
export function AnswerBadge({answered}) {
    return (
        <span className={`shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-bold ${answered ? "bg-brand-soft text-brand-fg" : "bg-up/10 text-up"}`}>
            {answered ? "답변 완료" : "답변 대기"}
        </span>
    );
}

const PAGE_SIZE = 15;
const SORTS = [
    {value: "latest", label: "최신순"},
    {value: "popular", label: "인기순"},
    {value: "views", label: "조회순"},
    {value: "oldest", label: "오래된순"},
];

/**
 * 커뮤니티 목록: 분류 · 정렬 · 쪽 이동. 분류·정렬·쪽은 주소에 남겨 뒤로 가기로 돌아올 수 있게 한다.
 * 문의 탭은 일반 사용자에게 내 문의만, 관리자에게 모든 문의를 보여 주고(서버가 거름) 답변 상태로 거를 수 있다
 */
export default function PostList() {
    const {user} = useAuth();
    const isAdmin = user?.role === "ROLE_ADMIN";
    const [params, setParams] = useSearchParams();
    const category = params.get("category") ?? "ALL";
    const sort = params.get("sort") ?? "latest";
    const page = Number(params.get("page") ?? 0);
    const inquiryTab = category === INQUIRY;
    const answered = inquiryTab ? params.get("answered") ?? "all" : "all";
    const [data, setData] = useState(null);

    const update = (next) => {
        const merged = {category, sort, page: 0, answered, ...next};
        // 답변 상태는 문의 탭에서만 주소에 남긴다
        if (merged.category !== INQUIRY || merged.answered === "all") delete merged.answered;
        setParams(merged, {replace: true});
    };

    useEffect(() => {
        let alive = true;
        setData(null);
        fetchPosts({
            page, size: PAGE_SIZE, category: category === "ALL" ? null : category, sort,
            answered: answered === "all" ? null : answered,
        })
            .then((res) => alive && setData(res))
            .catch((error) => {
                console.error("게시글 조회 실패:", error);
                if (alive) setData({content: [], totalPages: 0});
            });
        return () => {
            alive = false;
        };
    }, [category, sort, page, answered]);

    return (
        <div className="mx-auto flex max-w-[960px] flex-col gap-4 px-5 pt-4 pb-24 md:gap-5 md:pt-8 md:pb-14">
            <div className="flex items-center justify-between gap-3">
                <h1 className="text-xl font-extrabold md:text-[28px]">커뮤니티</h1>
                <Button as={Link} to={inquiryTab ? "/community/write?category=INQUIRY" : "/community/write"} size="sm" className="max-md:hidden">
                    {inquiryTab ? "문의하기" : "글쓰기"}
                </Button>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2">
                <SegmentedTabs label="분류" items={CATEGORIES} value={category} onChange={(value) => update({category: value})}/>
                <select
                    aria-label="정렬"
                    value={sort}
                    onChange={(e) => update({sort: e.target.value})}
                    className="h-10 rounded-xl border border-line bg-surface px-3 text-sm font-semibold"
                >
                    {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
            </div>

            {inquiryTab && (
                <div className="flex flex-col gap-3 rounded-2xl bg-surface-2 px-4 py-3 text-sm md:flex-row md:items-center md:justify-between">
                    <p className="text-fg-2">
                        {isAdmin
                            ? "모든 사용자의 문의예요. 댓글로 답하면 답변 완료가 되고 작성자에게 알림이 가요."
                            : "내가 쓴 문의만 보여요. 문의는 나와 관리자만 볼 수 있어요."}
                    </p>
                    <SegmentedTabs label="답변 상태" items={ANSWER_FILTERS} value={answered} onChange={(value) => update({answered: value})}/>
                </div>
            )}

            <Card className="overflow-hidden">
                {data === null && <p className="px-5 py-14 text-center text-sm text-fg-3">불러오는 중…</p>}
                {data?.content.length === 0 && (
                    <p className="px-5 py-14 text-center text-sm text-fg-3">
                        {inquiryTab ? "문의가 없어요. 궁금한 점이나 불편한 점을 남겨 주세요." : "아직 글이 없어요. 첫 글을 남겨 보세요!"}
                    </p>
                )}
                <ul>
                    {data?.content.map((post) => (
                        <li key={post.id} className="border-b border-line last:border-b-0">
                            <Link to={`/community/${post.id}`} className="flex flex-col gap-2 px-5 py-4 hover:bg-surface-2">
                                <span className="flex items-start gap-2">
                                    <span className={`mt-0.5 shrink-0 rounded-md px-1.5 py-0.5 text-[11px] font-bold ${CATEGORY_STYLE[post.category] ?? "bg-surface-2 text-fg-2"}`}>
                                        {CATEGORY_LABEL[post.category] ?? post.category}
                                    </span>
                                    {post.category === INQUIRY && <AnswerBadge answered={post.answered}/>}
                                    <span className="min-w-0 flex-1 text-[15px] font-semibold leading-snug">
                                        {post.title}
                                        {post.commentCount > 0 && <span className="num ml-1.5 text-[13px] font-bold text-up">{post.commentCount}</span>}
                                        {post.hasImage && (
                                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="ml-1.5 inline align-[-1px] text-fg-3" aria-label="사진 있음"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/></svg>
                                        )}
                                    </span>
                                </span>
                                <span className="num flex items-center gap-2 text-xs text-fg-3">
                                    <Avatar src={post.profileImage} name={post.name} tier={post.tier} size="xs"/>
                                    <span className="font-semibold text-fg-2">{post.name}</span>
                                    <span>· {postTime(post.createdAt)}</span>
                                    {post.category === INQUIRY ? (
                                        <span className="ml-auto">{INQUIRY_TYPE_LABEL[post.inquiryType] ?? "기타"}</span>
                                    ) : (
                                        <span className="ml-auto flex gap-3">
                                            <span>조회 {post.views}</span>
                                            <span>좋아요 {post.likes}</span>
                                        </span>
                                    )}
                                </span>
                            </Link>
                        </li>
                    ))}
                </ul>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={(p) => update({page: p})}/>

            {/* 모바일: 아래 탭 위에 떠 있는 글쓰기 버튼 */}
            <Link
                to={inquiryTab ? "/community/write?category=INQUIRY" : "/community/write"}
                aria-label={inquiryTab ? "문의하기" : "글쓰기"}
                className="fixed right-5 bottom-[calc(72px+env(safe-area-inset-bottom))] z-30 flex size-14 items-center justify-center rounded-full bg-brand text-[#111827] shadow-lg md:hidden"
            >
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
            </Link>
        </div>
    );
}
