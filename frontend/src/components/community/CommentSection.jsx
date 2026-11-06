import {useCallback, useEffect, useState} from 'react';
import Avatar from '../ui/Avatar.jsx';
import Button from '../ui/Button.jsx';
import {useAuth} from '../../auth/useAuth.js';
import {useToast} from '../toastContext.js';
import {createComment, deleteComment, fetchComments, sendReaction} from '../../api/communityApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {fullTime} from './post.js';

const MAX = 255;

function CommentInput({onSubmit, placeholder, autoFocus = false, onCancel}) {
    const [text, setText] = useState('');
    const [sending, setSending] = useState(false);

    const submit = async (e) => {
        e.preventDefault();
        if (!text.trim()) return;
        setSending(true);
        const ok = await onSubmit(text.trim());
        setSending(false);
        if (ok) setText('');
    };

    return (
        <form onSubmit={submit} className="flex flex-col gap-2">
            <div className="relative">
                <textarea
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    maxLength={MAX}
                    rows={3}
                    autoFocus={autoFocus}
                    placeholder={placeholder}
                    aria-label={placeholder}
                    className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-3 pb-7 text-sm leading-relaxed outline-none focus:border-fg-3"
                />
                <span className="num absolute right-3 bottom-2.5 text-[11px] text-fg-3">{text.length}/{MAX}</span>
            </div>
            <div className="flex justify-end gap-2">
                {onCancel && <Button variant="ghost" size="sm" onClick={onCancel}>취소</Button>}
                <Button type="submit" size="sm" disabled={sending || !text.trim()}>{sending ? '등록 중…' : '등록'}</Button>
            </div>
        </form>
    );
}

function Comment({comment, reply = false, myId, onReact, onDelete, onReply, replying, children}) {
    // onReact가 없으면(문의 글) 좋아요·싫어요를 보이지 않는다
    return (
        <div className={`flex gap-3 ${reply ? 'pt-3' : 'py-4'}`}>
            <Avatar src={comment.profileImage} name={comment.userName} tier={comment.userTier} size={reply ? 'xs' : 'sm'} outline="text-surface"/>
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                <span className="flex flex-wrap items-baseline gap-x-2 text-xs">
                    <span className="text-sm font-semibold">{comment.userName}</span>
                    <span className="num text-fg-3">{fullTime(comment.createdAt)}</span>
                </span>
                <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-fg-2">{comment.content}</p>
                <span className="flex flex-wrap items-center gap-1 text-xs text-fg-3">
                    {onReact && (
                        <>
                            <button type="button" onClick={() => onReact(comment.id, true)} className="num rounded-md px-1.5 py-1 hover:bg-surface-2 hover:text-fg">좋아요 {comment.likes}</button>
                            <button type="button" onClick={() => onReact(comment.id, false)} className="num rounded-md px-1.5 py-1 hover:bg-surface-2 hover:text-fg">싫어요 {comment.dislikes}</button>
                        </>
                    )}
                    {!reply && (
                        <button type="button" onClick={onReply} aria-expanded={replying} className="rounded-md px-1.5 py-1 hover:bg-surface-2 hover:text-fg">
                            {replying ? '답글 닫기' : '답글'}
                        </button>
                    )}
                    {comment.userId === myId && (
                        <button type="button" onClick={() => onDelete(comment.id)} className="rounded-md px-1.5 py-1 hover:bg-surface-2 hover:text-up">삭제</button>
                    )}
                </span>
                {children}
            </div>
        </div>
    );
}

/** 댓글·답글 (답글은 한 단계까지) */
/**
 * 댓글 목록과 입력. inquiry면 문의 글: 반응을 숨기고, 작성자·관리자 사이의 답변으로 안내한다
 */
export default function CommentSection({postId, inquiry = false}) {
    const reactHandler = inquiry ? undefined : (id, liked) => react(id, liked);
    const {user} = useAuth();
    const {errorAlert} = useToast();
    const [comments, setComments] = useState(null);
    const [replyTo, setReplyTo] = useState(null);

    const reload = useCallback(() => {
        fetchComments(postId)
            .then((list) => setComments(Array.isArray(list) ? list : []))
            .catch(() => setComments([]));
    }, [postId]);

    useEffect(() => {
        reload();
    }, [reload]);

    const write = async (content, parentId = null) => {
        try {
            await createComment({postId, content, parentId});
            setReplyTo(null);
            reload();
            return true;
        } catch (err) {
            errorAlert(getErrorMessage(err, '댓글을 등록하지 못했습니다.'));
            return false;
        }
    };

    const react = async (commentId, liked) => {
        try {
            await sendReaction({targetType: 'COMMENT', targetId: commentId, liked});
            reload();
        } catch (err) {
            errorAlert(getErrorMessage(err, '반응을 남기지 못했습니다.'));
        }
    };

    const remove = async (commentId) => {
        if (!window.confirm('댓글을 삭제할까요?')) return;
        try {
            await deleteComment(commentId);
            reload();
        } catch (err) {
            errorAlert(getErrorMessage(err, '댓글을 삭제하지 못했습니다.'));
        }
    };

    const count = (comments ?? []).reduce((n, c) => n + 1 + (c.children?.length ?? 0), 0);

    return (
        <section aria-label="댓글" className="flex flex-col gap-4">
            <h2 className="text-base font-bold">{inquiry ? '답변' : '댓글'} <span className="num text-up">{count}</span></h2>
            <CommentInput onSubmit={(text) => write(text)} placeholder={inquiry ? '답변이나 추가로 궁금한 점을 남겨 주세요' : '댓글을 남겨 보세요'}/>
            {comments?.length === 0 && (
                <p className="py-6 text-center text-sm text-fg-3">{inquiry ? '관리자가 확인한 뒤 답변을 남길 거예요.' : '첫 댓글을 남겨 보세요.'}</p>
            )}
            <ul className="divide-y divide-line">
                {comments?.map((c) => (
                    <li key={c.id}>
                        <Comment
                            comment={c}
                            myId={user?.id}
                            onReact={reactHandler}
                            onDelete={remove}
                            replying={replyTo === c.id}
                            onReply={() => setReplyTo(replyTo === c.id ? null : c.id)}
                        >
                            {c.children?.map((r) => (
                                <Comment key={r.id} comment={r} reply myId={user?.id} onReact={reactHandler} onDelete={remove}/>
                            ))}
                            {replyTo === c.id && (
                                <div className="pt-3">
                                    <CommentInput onSubmit={(text) => write(text, c.id)} placeholder="답글을 남겨 보세요" autoFocus onCancel={() => setReplyTo(null)}/>
                                </div>
                            )}
                        </Comment>
                    </li>
                ))}
            </ul>
        </section>
    );
}
