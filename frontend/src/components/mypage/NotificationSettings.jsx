import {useState} from 'react';
import Card from '../ui/Card.jsx';
import {useNotifications} from '../../notifications/useNotifications.js';
import {useToast} from '../toastContext.js';

// 받을 알림 종류 (끄면 저장도 실시간 전송도 하지 않는다)
const KINDS = [
    {key: 'orderExecution', label: '예약 주문 체결', desc: '걸어 둔 예약 매수·매도가 체결됐을 때'},
    {key: 'comment', label: '댓글·답글', desc: '내 글에 댓글, 내 댓글에 답글, 내 문의에 답변이 달렸을 때'},
    {key: 'notice', label: '공지사항', desc: '새 공지가 올라왔을 때'},
    {key: 'seasonUpdate', label: '시즌', desc: '새 시즌과 스플릿 2가 시작될 때'},
];

function Switch({checked, onChange, label, describedBy}) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            aria-label={label}
            aria-describedby={describedBy}
            onClick={() => onChange(!checked)}
            className={`relative h-7 w-12 shrink-0 rounded-full transition-colors ${checked ? 'bg-brand' : 'bg-line'}`}
        >
            <span className={`absolute top-0.5 left-0.5 size-6 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-5' : ''}`}/>
        </button>
    );
}

function Row({id, label, desc, checked, onChange}) {
    return (
        <li className="flex items-center gap-4 border-b border-line py-3.5 last:border-b-0">
            <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-[15px] font-semibold">{label}</span>
                <span id={id} className="text-xs leading-relaxed text-fg-3">{desc}</span>
            </span>
            <Switch checked={checked} onChange={onChange} label={label} describedBy={id}/>
        </li>
    );
}

/** 마이페이지 > 알림 설정: 종류별 받기, 실시간 토스트 표시. 바꾸면 바로 저장된다 */
export default function NotificationSettings() {
    const {settings, updateSettings} = useNotifications();
    const {errorAlert} = useToast();
    const [saving, setSaving] = useState(false);

    const change = async (patch) => {
        setSaving(true);
        try {
            await updateSettings(patch);
        } catch {
            errorAlert('알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <>
            <Card className="flex flex-col gap-1 px-5 pt-5 pb-2 md:px-6" aria-labelledby="notify-kinds">
                <h2 id="notify-kinds" className="text-base font-bold">받을 알림</h2>
                <p className="text-[13px] text-fg-3">끈 알림은 알림 목록에도 쌓이지 않아요.</p>
                <ul className="mt-1" aria-busy={saving}>
                    {KINDS.map((k) => (
                        <Row key={k.key} id={`notify-${k.key}`} label={k.label} desc={k.desc}
                             checked={settings[k.key]} onChange={(v) => change({[k.key]: v})}/>
                    ))}
                </ul>
            </Card>
            <Card className="flex flex-col gap-1 px-5 pt-5 pb-2 md:px-6" aria-labelledby="notify-display">
                <h2 id="notify-display" className="text-base font-bold">표시 방법</h2>
                <ul className="mt-1">
                    <Row id="notify-toast" label="실시간 알림 팝업"
                         desc="화면 위쪽에 잠깐 띄워요. 끄면 알림 목록과 배지 숫자로만 알려 드려요."
                         checked={settings.toast} onChange={(v) => change({toast: v})}/>
                </ul>
            </Card>
            <p className="px-1 text-xs text-fg-3">알림은 90일 동안 보관하고, 그보다 오래된 알림은 자동으로 지워져요.</p>
        </>
    );
}
