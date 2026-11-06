import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import Pagination from '../ui/Pagination.jsx';
import {fetchAuditLogs} from '../../api/adminApi.js';
import {ACTIONS, shortTime} from './adminFormat.js';

const PAGE_SIZE = 20;

/** 관리자 작업 기록: 누가 언제 무엇을 했는지 (읽기만) */
export default function AdminAuditLog() {
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);

    useEffect(() => {
        let alive = true;
        fetchAuditLogs({page, size: PAGE_SIZE})
            .then((res) => alive && setData(res))
            .catch(() => alive && setData({content: [], totalPages: 0}));
        return () => {
            alive = false;
        };
    }, [page]);

    return (
        <div className="flex flex-col gap-3">
            <Card className="overflow-hidden">
                {data === null && <p className="px-5 py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
                {data?.content.length === 0 && <p className="px-5 py-10 text-center text-sm text-fg-3">아직 기록이 없어요.</p>}
                <ul>
                    {data?.content.map((a) => (
                        <li key={a.id} className="flex flex-col gap-0.5 border-b border-line px-5 py-3 last:border-b-0">
                            <span className="flex items-center gap-2 text-sm">
                                <span className="font-bold">{ACTIONS[a.action] ?? a.action}</span>
                                <span className="text-xs text-fg-3">{a.targetType} {a.targetId ?? ''}</span>
                            </span>
                            {a.detail && <span className="line-clamp-2 text-[13px] text-fg-2">{a.detail}</span>}
                            <span className="num text-xs text-fg-3">{a.adminName} · {shortTime(a.createdAt)}</span>
                        </li>
                    ))}
                </ul>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages ?? 0} onChange={setPage}/>
        </div>
    );
}
