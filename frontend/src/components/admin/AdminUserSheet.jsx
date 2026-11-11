import {useEffect, useState} from 'react';
import Button from '../ui/Button.jsx';
import Sheet from '../ui/Sheet.jsx';
import Avatar from '../ui/Avatar.jsx';
import {useToast} from '../toastContext.js';
import {fetchAdminUser, suspendUser, unsuspendUser} from '../../api/adminApi.js';
import {getErrorMessage} from '../../api/apiError.js';
import {TIER_NAMES} from '../../constants/tiers.js';
import {formatKRW, formatNumber} from '../../utils/format.js';
import {PROVIDERS, shortDate, shortTime} from './adminFormat.js';

export function StatusBadge({u}) {
    if (u.withdrawn) return <span className="shrink-0 rounded-md bg-surface-2 px-1.5 text-[11px] font-bold text-fg-3">탈퇴</span>;
    if (u.suspendedAt) return <span className="shrink-0 rounded-md bg-up/10 px-1.5 text-[11px] font-bold text-up">정지</span>;
    if (u.admin) return <span className="shrink-0 rounded-md bg-brand-soft px-1.5 text-[11px] font-bold text-brand-fg">관리자</span>;
    return null;
}

/** 사용자 상세 + 이용 정지·해제. 관리자 페이지의 어느 영역에서든 사용자 번호로 연다 */
export default function AdminUserSheet({id, onClose, onChanged}) {
    const {infoAlert, errorAlert} = useToast();
    const [detail, setDetail] = useState(null);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        if (!id) return;
        setDetail(null);
        setReason('');
        fetchAdminUser(id).then(setDetail).catch((e) => errorAlert(getErrorMessage(e, '사용자를 불러오지 못했어요.')));
    }, [id, errorAlert]);

    const act = async (fn, done) => {
        setBusy(true);
        try {
            await fn();
            infoAlert(done);
            setReason('');
            setDetail(await fetchAdminUser(id));
            onChanged();
        } catch (e) {
            errorAlert(getErrorMessage(e, '처리하지 못했어요.'));
        } finally {
            setBusy(false);
        }
    };

    const suspend = () => {
        if (!reason.trim()) return errorAlert('정지 사유를 입력해주세요.');
        if (!window.confirm(`${detail.user.name} #${id}님을 이용 정지할까요? 바로 로그아웃되고 다시 로그인할 수 없어요.`)) return;
        act(() => suspendUser(id, reason.trim()), '이용을 정지했어요.');
    };

    const u = detail?.user;
    return (
        <Sheet open={Boolean(id)} onClose={onClose} title="사용자 상세">
            <div className="flex flex-col gap-4 p-5">
                {!detail ? (
                    <p className="py-8 text-center text-sm text-fg-3">불러오는 중…</p>
                ) : (
                    <>
                        <div className="flex items-center gap-3">
                            <Avatar src={u.profileImage} tier={detail.currentTier ?? u.tier} size="lg" badge={false}/>
                            <div className="flex min-w-0 flex-col">
                                <div className="flex items-center gap-2">
                                    <h3 className="truncate text-lg font-extrabold">{u.name}</h3>
                                    <StatusBadge u={u}/>
                                </div>
                                <span className="num text-sm text-fg-3">#{u.id} · {PROVIDERS[u.provider] ?? u.provider} · {shortDate(u.createdAt)} 가입</span>
                            </div>
                        </div>
                        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                            <dt className="text-fg-3">이메일</dt><dd>{u.email ?? '—'}</dd>
                            <dt className="text-fg-3">이번 시즌</dt>
                            <dd className="num">{detail.rank ? `${detail.rank}위 · ${TIER_NAMES[detail.currentTier] ?? '—'} · ${formatKRW(detail.totalValue)}원` : '순위 없음'}</dd>
                            <dt className="text-fg-3">현금</dt><dd className="num">{formatKRW(detail.cash)}원</dd>
                            <dt className="text-fg-3">활동</dt><dd className="num">게시글 {detail.posts} · 댓글 {detail.comments} · 체결 {formatNumber(detail.trades)}</dd>
                            {u.suspendedAt && (<><dt className="text-fg-3">정지</dt><dd className="text-up">{shortTime(u.suspendedAt)} · {detail.suspendedReason}</dd></>)}
                        </dl>

                        {detail.recentOrders.length > 0 && (
                            <div className="flex flex-col gap-1">
                                <span className="text-xs font-semibold text-fg-3">최근 주문</span>
                                <ul className="max-h-48 overflow-y-auto rounded-xl bg-surface-2 px-3 py-1.5 text-xs">
                                    {detail.recentOrders.map((o) => (
                                        <li key={o.id} className="num flex justify-between gap-2 py-1">
                                            <span className="w-20 text-fg-3">{shortTime(o.createdAt)}</span>
                                            <span className={o.orderType === 'BUY' ? 'text-up' : 'text-down'}>{o.orderType === 'BUY' ? '매수' : '매도'}</span>
                                            <span className="min-w-0 flex-1 truncate">{o.coin}</span>
                                            <span className="text-fg-3">{o.reserved ? '예약' : '시장가'}{o.status === 'PENDING' ? '·대기' : ''}</span>
                                        </li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        {!u.admin && !u.withdrawn && (u.suspendedAt ? (
                            <Button variant="secondary" disabled={busy} onClick={() => act(() => unsuspendUser(id), '정지를 해제했어요.')}>정지 해제</Button>
                        ) : (
                            <div className="flex flex-col gap-2 border-t border-line pt-4">
                                <label className="text-xs font-semibold text-fg-3" htmlFor="suspend-reason">이용 정지 사유 (사용자에게 보이지 않음)</label>
                                <input id="suspend-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200}
                                       placeholder="예: 반복 도배, 욕설" className="h-11 rounded-xl border border-line bg-surface px-3 text-sm outline-none focus:border-fg-3"/>
                                <Button disabled={busy} onClick={suspend} className="bg-up text-white hover:bg-up/90">이용 정지</Button>
                            </div>
                        ))}
                    </>
                )}
                <Button variant="secondary" onClick={onClose}>닫기</Button>
            </div>
        </Sheet>
    );
}
