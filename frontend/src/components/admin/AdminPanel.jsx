import {useSearchParams} from 'react-router-dom';
import AdminOverview from './AdminOverview.jsx';
import AdminUsers from './AdminUsers.jsx';
import AdminCommunity from './AdminCommunity.jsx';
import AdminNotices from './AdminNotices.jsx';
import AdminAuditLog from './AdminAuditLog.jsx';

const SECTIONS = [
    {value: 'overview', label: '현황', Component: AdminOverview},
    {value: 'users', label: '사용자', Component: AdminUsers},
    {value: 'community', label: '커뮤니티', Component: AdminCommunity},
    {value: 'notices', label: '공지', Component: AdminNotices},
    {value: 'audit', label: '작업 기록', Component: AdminAuditLog},
];

/**
 * 관리자 페이지 (마이페이지의 관리자 탭). 고른 영역은 주소(?section=)에 남겨 새로고침해도 유지한다.
 * 권한은 서버(/admin/**)가 확인하고, 이 화면은 관리자에게만 보일 뿐이다
 */
export default function AdminPanel() {
    const [params, setParams] = useSearchParams();
    const current = SECTIONS.find((s) => s.value === params.get('section')) ?? SECTIONS[0];
    const Section = current.Component;

    return (
        <div className="flex flex-col gap-4">
            <h2 className="text-lg font-extrabold md:text-[22px]">관리자</h2>
            <div role="tablist" aria-label="관리 영역" className="flex flex-wrap gap-1.5">
                {SECTIONS.map((s) => (
                    <button
                        key={s.value}
                        type="button"
                        role="tab"
                        aria-selected={s.value === current.value}
                        onClick={() => setParams({tab: 'admin', section: s.value})}
                        className={`h-9 shrink-0 rounded-full border px-3.5 text-[13px] font-semibold ${
                            s.value === current.value ? 'border-fg bg-fg text-bg' : 'border-line bg-surface text-fg-2 hover:text-fg'
                        }`}
                    >
                        {s.label}
                    </button>
                ))}
            </div>
            <Section key={current.value}/>
        </div>
    );
}
