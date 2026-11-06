import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import Pagination from '../ui/Pagination.jsx';
import RichEditor from '../ui/RichEditor.jsx';
import {useToast} from '../toastContext.js';
import {createNotice, deleteNotice, fetchNotices, updateNotice} from '../../api/noticeApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {shortDate} from './adminFormat.js';

const PAGE_SIZE = 10;

/** 공지 등록·수정 (수정은 알림을 다시 보내지 않는다) */
function NoticeForm({notice, onDone, onCancel}) {
    const {infoAlert, errorAlert} = useToast();
    const [title, setTitle] = useState(notice?.title ?? '');
    const [content, setContent] = useState(notice?.content ?? '');
    const [notify, setNotify] = useState(true);
    const [saving, setSaving] = useState(false);
    const editing = Boolean(notice);

    const submit = async (e) => {
        e.preventDefault();
        // 에디터는 빈 글도 <p><br></p>로 남기므로 태그를 뺀 글자로 확인한다
        if (!title.trim() || !content.replace(/<[^>]*>/g, '').trim()) return errorAlert('제목과 내용을 입력해주세요.');
        setSaving(true);
        try {
            if (editing) await updateNotice(notice.id, {title: title.trim(), content});
            else await createNotice({title: title.trim(), content, notify});
            infoAlert(editing ? '공지를 수정했어요.' : '공지를 등록했어요.');
            onDone();
        } catch (err) {
            errorAlert(getErrorMessage(err, '저장하지 못했어요.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card as="form" onSubmit={submit} className="flex flex-col gap-4 p-5">
            <h3 className="text-base font-bold">{editing ? '공지 수정' : '공지 등록'}</h3>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목" aria-label="제목"
                   className="h-12 rounded-xl border border-line bg-surface px-4 text-base font-semibold outline-none focus:border-fg-3"/>
            <RichEditor value={content} onChange={setContent} placeholder="공지 내용을 입력하세요"/>
            {!editing && (
                <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={notify} onChange={(e) => setNotify(e.target.checked)} className="size-4 accent-[var(--color-brand)]"/>
                    모든 사용자에게 알림 보내기
                </label>
            )}
            <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={onCancel}>취소</Button>
                <Button type="submit" disabled={saving}>{saving ? '저장 중…' : editing ? '수정' : '등록'}</Button>
            </div>
        </Card>
    );
}

/** 공지 목록 · 등록 · 수정 · 삭제 */
export default function AdminNotices() {
    const {infoAlert, errorAlert} = useToast();
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [editing, setEditing] = useState(undefined); // undefined: 목록, null: 새 공지, 객체: 수정
    const [version, setVersion] = useState(0);

    useEffect(() => {
        if (editing !== undefined) return;
        let alive = true;
        fetchNotices({page, size: PAGE_SIZE})
            .then((res) => alive && setData(res))
            .catch(() => alive && setData({content: [], totalPages: 0}));
        return () => {
            alive = false;
        };
    }, [page, editing, version]);

    const remove = async (n) => {
        if (!window.confirm(`'${n.title}' 공지를 삭제할까요?`)) return;
        try {
            await deleteNotice(n.id);
            infoAlert('삭제했어요.');
            setVersion((v) => v + 1);
        } catch (e) {
            errorAlert(getErrorMessage(e, '삭제하지 못했어요.'));
        }
    };

    if (editing !== undefined) {
        return <NoticeForm key={editing?.id ?? 'new'} notice={editing} onDone={() => setEditing(undefined)} onCancel={() => setEditing(undefined)}/>;
    }

    return (
        <div className="flex flex-col gap-3">
            <Button size="sm" onClick={() => setEditing(null)} className="self-start">공지 등록</Button>
            <Card className="overflow-hidden">
                {data === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
                {data?.content.length === 0 && <p className="px-5 py-10 text-center text-sm text-fg-3">등록된 공지가 없어요.</p>}
                <ul>
                    {data?.content.map((n) => (
                        <li key={n.id} className="flex items-center gap-1 border-b border-line pr-3 last:border-b-0">
                            <span className="flex min-w-0 flex-1 flex-col gap-0.5 px-5 py-3">
                                <span className="truncate text-[15px] font-semibold">{n.title}</span>
                                <span className="num text-xs text-fg-3">{n.writer} · {shortDate(n.createdAt)}</span>
                            </span>
                            <Button size="sm" variant="ghost" onClick={() => setEditing(n)}>수정</Button>
                            <Button size="sm" variant="ghost" onClick={() => remove(n)} className="text-up">삭제</Button>
                        </li>
                    ))}
                </ul>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={setPage}/>
        </div>
    );
}
