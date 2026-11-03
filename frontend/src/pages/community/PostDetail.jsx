import {useEffect, useState} from "react";
import {Link, useNavigate, useParams} from "react-router-dom";
import {useAuth} from "../../auth/useAuth.js";
import {useToast} from "../../components/toastContext.js";
import {deletePost, fetchPost, sendReaction} from "../../api/communityApi.js";
import {getErrorMessage} from "../../api/apiError.js";
import Card from "../../components/ui/Card.jsx";
import Avatar from "../../components/ui/Avatar.jsx";
import TierBadge from "../../components/ui/TierBadge.jsx";
import RichContent from "../../components/ui/RichContent.jsx";
import CommentSection from "../../components/community/CommentSection.jsx";
import {CATEGORY_LABEL, CATEGORY_STYLE, fullTime} from "../../components/community/post.js";
import {TIER_NAMES, TIER_TEXT} from "../../constants/tiers.js";

/** 게시글 본문 · 글쓴이 티어 기록 · 좋아요/싫어요 · 댓글 */
export default function PostDetail() {
    const {id} = useParams();
    const postId = Number(id);
    const navigate = useNavigate();
    const {user} = useAuth();
    const {infoAlert, errorAlert} = useToast();
    const [post, setPost] = useState(null);

    // 조회수는 서버가 사용자 기준으로 중복을 걸러 올린다
    useEffect(() => {
        let alive = true;
        fetchPost(postId)
            .then((p) => alive && setPost(p))
            .catch((err) => {
                if (!alive) return;
                errorAlert(getErrorMessage(err, "게시글을 불러오지 못했습니다."));
                navigate("/community", {replace: true});
            });
        return () => {
            alive = false;
        };
    }, [postId, navigate, errorAlert]);

    const react = async (liked) => {
        try {
            await sendReaction({targetType: "POST", targetId: postId, liked});
            setPost(await fetchPost(postId));
        } catch (err) {
            errorAlert(getErrorMessage(err, "반응을 남기지 못했습니다."));
        }
    };

    const remove = async () => {
        if (!window.confirm("이 글을 삭제할까요?")) return;
        try {
            await deletePost(postId);
            infoAlert("글을 삭제했어요.");
            navigate("/community");
        } catch (err) {
            errorAlert(getErrorMessage(err, "글을 삭제하지 못했습니다."));
        }
    };

    if (!post) return <p className="py-20 text-center text-sm text-fg-3">불러오는 중…</p>;

    return (
        <div className="mx-auto flex max-w-[800px] flex-col gap-4 px-5 pt-3 pb-12 md:gap-5 md:pt-8">
            <Link to="/community" className="flex w-fit items-center gap-1 text-sm font-semibold text-fg-2 hover:text-fg">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 6l-6 6 6 6"/></svg>
                커뮤니티
            </Link>

            <Card as="article" className="flex flex-col gap-5 p-5 md:p-8">
                <header className="flex flex-col gap-3 border-b border-line pb-5">
                    <span className={`w-fit rounded-md px-1.5 py-0.5 text-xs font-bold ${CATEGORY_STYLE[post.category] ?? "bg-surface-2 text-fg-2"}`}>
                        {CATEGORY_LABEL[post.category] ?? post.category}
                    </span>
                    <h1 className="text-xl leading-snug font-extrabold tracking-[-0.01em] md:text-2xl">{post.title}</h1>
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                        <Avatar src={post.profileImage} name={post.name} tier={post.tier} size="sm"/>
                        <span className="flex flex-col">
                            <span className="text-sm font-semibold">{post.name}</span>
                            <span className="num text-xs text-fg-3">{fullTime(post.createdAt)} · 조회 {post.views}</span>
                        </span>
                        {user?.id === post.userId && (
                            <button type="button" onClick={remove} className="ml-auto rounded-lg px-2.5 py-1.5 text-[13px] font-semibold text-fg-3 hover:bg-surface-2 hover:text-up">
                                삭제
                            </button>
                        )}
                    </div>
                </header>

                <RichContent html={post.content}/>

                <div className="flex justify-center gap-2 pt-2">
                    <button type="button" onClick={() => react(true)} className="num flex h-11 items-center gap-1.5 rounded-full border border-line px-5 text-sm font-semibold hover:border-up hover:text-up">
                        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M7 10v11H3V10zM7 10l4-8a3 3 0 0 1 3 3v4h6a2 2 0 0 1 2 2.3l-1.4 8A2 2 0 0 1 18.6 21H7"/></svg>
                        좋아요 {post.likes}
                    </button>
                    <button type="button" onClick={() => react(false)} className="num flex h-11 items-center gap-1.5 rounded-full border border-line px-5 text-sm font-semibold hover:border-down hover:text-down">
                        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="rotate-180" aria-hidden="true"><path d="M7 10v11H3V10zM7 10l4-8a3 3 0 0 1 3 3v4h6a2 2 0 0 1 2 2.3l-1.4 8A2 2 0 0 1 18.6 21H7"/></svg>
                        싫어요 {post.dislikes}
                    </button>
                </div>

                {/* 글쓴이 티어 기록 */}
                <div className="flex flex-wrap items-center gap-3 rounded-2xl bg-surface-2 p-4">
                    <Avatar src={post.profileImage} name={post.name} tier={post.tier} size="md" outline="text-surface-2"/>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <span className="text-sm font-semibold">{post.name}</span>
                        <span className="text-xs text-fg-3">
                            최고 티어{" "}
                            {post.highestTier ? <strong className={TIER_TEXT[post.highestTier]}>{TIER_NAMES[post.highestTier]}</strong> : "기록 없음"}
                        </span>
                    </span>
                    {post.pastSeasonTiers?.length > 0 && (
                        <ul className="flex gap-1.5" aria-label="지난 시즌 티어">
                            {post.pastSeasonTiers.slice(0, 5).map((s) => (
                                <li key={s.name} title={`${s.name} ${TIER_NAMES[s.tier] ?? ""}`}>
                                    <TierBadge tier={s.tier} size={24}/>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </Card>

            <Card className="p-5 md:p-8">
                <CommentSection postId={postId}/>
            </Card>
        </div>
    );
}
