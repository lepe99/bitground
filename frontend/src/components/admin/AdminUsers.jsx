import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import Avatar from '../ui/Avatar.jsx';
import Pagination from '../ui/Pagination.jsx';
import {fetchAdminUsers} from '../../api/adminApi.js';
import {PROVIDERS, shortDate} from './adminFormat.js';
import {StatusBadge} from './AdminUserSheet.jsx';
import {useAdminUser} from './adminUserContext.js';

const PAGE_SIZE = 20;

/** 사용자 검색·목록. 닉네임은 겹칠 수 있어 번호(#)를 함께 보여 주고, 번호·이메일 전체로도 찾는다 */
export default function AdminUsers() {
    const {openUser, version} = useAdminUser();
    const [query, setQuery] = useState('');
    const [q, setQ] = useState('');
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);

    useEffect(() => {
        const timer = setTimeout(() => {
            setQ(query.trim());
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [query]);

    useEffect(() => {
        let alive = true;
        fetchAdminUsers({q, page, size: PAGE_SIZE})
            .then((res) => alive && setData(res))
            .catch(() => alive && setData({content: [], totalPages: 0}));
        return () => {
            alive = false;
        };
    }, [q, page, version]);

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
                <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="#번호 · 이메일 · 닉네임" aria-label="사용자 검색"
                       className="h-11 rounded-xl border border-line bg-surface px-4 text-[15px] outline-none focus:border-fg-3"/>
                <span className="px-1 text-xs text-fg-3">#33처럼 번호로 찾거나, 이메일은 전체 주소로 찾아요. 닉네임은 겹칠 수 있어요.</span>
            </div>
            <Card className="overflow-hidden">
                {data === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
                {data?.content.length === 0 && <p className="px-5 py-10 text-center text-sm text-fg-3">사용자가 없어요.</p>}
                <ul>
                    {data?.content.map((u) => (
                        <li key={u.id} className="border-b border-line last:border-b-0">
                            <button type="button" onClick={() => openUser(u.id)} className="flex w-full items-center gap-3 px-5 py-3 text-left hover:bg-surface-2">
                                <Avatar src={u.profileImage} tier={u.tier} size="sm" badge={false}/>
                                <span className="flex min-w-0 flex-1 flex-col">
                                    <span className="flex min-w-0 items-baseline gap-1.5">
                                        <span className="truncate text-[15px] font-semibold">{u.name}</span>
                                        <span className="num shrink-0 text-sm text-fg-3">#{u.id}</span>
                                        <StatusBadge u={u}/>
                                    </span>
                                    <span className="truncate text-xs text-fg-3">{PROVIDERS[u.provider] ?? u.provider} · {u.email ?? '이메일 없음'}</span>
                                </span>
                                <span className="num shrink-0 text-xs text-fg-3">{shortDate(u.createdAt)}</span>
                            </button>
                        </li>
                    ))}
                </ul>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={setPage}/>
        </div>
    );
}
