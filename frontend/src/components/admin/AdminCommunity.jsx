import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import Card from '../ui/Card.jsx';
import SegmentedTabs from '../ui/SegmentedTabs.jsx';
import Pagination from '../ui/Pagination.jsx';
import {useToast} from '../toastContext.js';
import {deleteAdminComment, deleteAdminPost, fetchAdminComments, fetchAdminPosts} from '../../api/adminApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {shortTime} from './adminFormat.js';
import UserTag from './UserTag.jsx';

const KINDS = [
    {value: 'posts', label: '게시글'},
    {value: 'comments', label: '댓글'},
];
const CATEGORIES = {CHAT: '자유', INFO: '정보', QUESTION: '질문'};
const PAGE_SIZE = 20;

const TrashButton = ({label, onClick}) => (
    <button type="button" onClick={onClick} aria-label={label} className="flex size-9 shrink-0 items-center justify-center rounded-lg text-fg-3 hover:bg-surface-2 hover:text-up">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
    </button>
);

/** 최근 게시글·댓글을 모아 보고 지운다 (지우면 작업 기록에 남음) */
export default function AdminCommunity() {
    const {infoAlert, errorAlert} = useToast();
    const [kind, setKind] = useState('posts');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [version, setVersion] = useState(0);

    useEffect(() => {
        let alive = true;
        setData(null);
        (kind === 'posts' ? fetchAdminPosts : fetchAdminComments)({page, size: PAGE_SIZE})
            .then((res) => alive && setData(res))
            .catch(() => alive && setData({content: [], totalPages: 0}));
        return () => {
            alive = false;
        };
    }, [kind, page, version]);

    const remove = async (id, label) => {
        if (!window.confirm(`${label}을(를) 삭제할까요? 작업 기록에 남아요.`)) return;
        try {
            await (kind === 'posts' ? deleteAdminPost : deleteAdminComment)(id);
            infoAlert('삭제했어요.');
            setVersion((v) => v + 1);
        } catch (e) {
            errorAlert(getErrorMessage(e, '삭제하지 못했어요.'));
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <SegmentedTabs label="종류" items={KINDS} value={kind} onChange={(v) => {
                setKind(v);
                setPage(0);
            }} className="self-start"/>
            <Card className="overflow-hidden">
                {data === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
                {data?.content.length === 0 && <p className="px-5 py-10 text-center text-sm text-fg-3">없어요.</p>}
                <ul>
                    {kind === 'posts' && data?.content.map((p) => (
                        <li key={p.id} className="flex items-center gap-2 border-b border-line pr-3 last:border-b-0">
                            <div className={`flex min-w-0 flex-1 flex-col gap-0.5 px-5 py-3 ${p.deleted ? 'opacity-50' : ''}`}>
                                <Link to={`/community/${p.id}`} className="truncate text-[15px] font-semibold hover:underline">
                                    <span className="mr-1.5 text-xs font-bold text-fg-3">{CATEGORIES[p.category] ?? p.category}</span>
                                    {p.title}
                                    {p.deleted && <span className="ml-1.5 text-xs text-fg-3">(삭제됨)</span>}
                                </Link>
                                <span className="num flex min-w-0 items-baseline gap-1 text-xs text-fg-3">
                                    <UserTag id={p.authorId} name={p.authorName} className="text-fg-2"/>
                                    <span className="shrink-0">· {shortTime(p.createdAt)} · 댓글 {p.comments}</span>
                                </span>
                            </div>
                            {!p.deleted && <TrashButton label={`${p.title} 삭제`} onClick={() => remove(p.id, '이 게시글')}/>}
                        </li>
                    ))}
                    {kind === 'comments' && data?.content.map((c) => (
                        <li key={c.id} className="flex items-center gap-2 border-b border-line pr-3 last:border-b-0">
                            <div className="flex min-w-0 flex-1 flex-col gap-0.5 px-5 py-3">
                                <Link to={`/community/${c.postId}`} className="line-clamp-2 text-sm hover:underline">{c.content}</Link>
                                <span className="num flex min-w-0 items-baseline gap-1 text-xs text-fg-3">
                                    <UserTag id={c.authorId} name={c.authorName} className="text-fg-2"/>
                                    <span className="truncate">· {shortTime(c.createdAt)} · {c.postTitle}</span>
                                </span>
                            </div>
                            <TrashButton label="댓글 삭제" onClick={() => remove(c.id, '이 댓글')}/>
                        </li>
                    ))}
                </ul>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={setPage}/>
        </div>
    );
}
