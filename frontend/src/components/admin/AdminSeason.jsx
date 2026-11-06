import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import Button from '../ui/Button.jsx';
import {useToast} from '../toastContext.js';
import {fetchCurrentSeason} from '../../api/seasonApi.js';
import {updateSeasonSchedule} from '../../api/adminApi.js';
import {getErrorMessage} from '../../api/apiError.js';

const HOURS = Array.from({length: 24}, (_, h) => h);
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 서버 시각 문자열(한국 시각, 시간대 없음) ↔ 날짜·시 입력
const split = (s) => ({date: s?.slice(0, 10) ?? '', hour: s ? Number(s.slice(11, 13)) : 0});
const join = ({date, hour}) => `${date}T${String(hour).padStart(2, '0')}:00:00`;
const label = (s) => {
    if (!s) return '—';
    const d = new Date(s);
    return `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${d.getHours()}시`;
};

function HourInput({name, value, onChange, disabled}) {
    return (
        <div className="flex gap-2">
            <input
                type="date"
                aria-label={`${name} 날짜`}
                value={value.date}
                disabled={disabled}
                onChange={(e) => onChange({...value, date: e.target.value})}
                className="h-11 min-w-0 flex-1 rounded-xl border border-line bg-surface px-3 text-sm outline-none focus:border-fg-3 disabled:opacity-50"
            />
            <select
                aria-label={`${name} 시각`}
                value={value.hour}
                disabled={disabled}
                onChange={(e) => onChange({...value, hour: Number(e.target.value)})}
                className="h-11 w-24 rounded-xl border border-line bg-surface px-3 text-sm outline-none focus:border-fg-3 disabled:opacity-50"
            >
                {HOURS.map((h) => <option key={h} value={h}>{h}시</option>)}
            </select>
        </div>
    );
}

/**
 * 진행 중인 시즌 일정 바꾸기 (정각 단위). 스케줄러가 매시 정각에 DB 일정을 읽어 반영한다.
 * 이미 시작된 스플릿 2와, 종료 시각이 지나 정산을 기다리는 시즌은 바꿀 수 없다 (서버가 확인)
 */
export default function AdminSeason() {
    const {infoAlert, errorAlert} = useToast();
    const [season, setSeason] = useState(null);
    const [splitAt, setSplitAt] = useState(split(null));
    const [endsAt, setEndsAt] = useState(split(null));
    const [saving, setSaving] = useState(false);

    const apply = (s) => {
        setSeason(s);
        setSplitAt(split(s.splitStartsAt));
        setEndsAt(split(s.endsAt));
    };

    useEffect(() => {
        fetchCurrentSeason().then(apply).catch(() => setSeason(false));
    }, []);

    if (season === null) return <Card className="p-5 text-sm text-fg-3">불러오는 중…</Card>;
    if (season === false) return <Card className="p-5 text-sm text-fg-3">진행 중인 시즌이 없어요.</Card>;

    const now = new Date(season.now).getTime();
    const splitStarted = season.splitStartsAt && now >= new Date(season.splitStartsAt).getTime();
    const settling = season.endsAt && now >= new Date(season.endsAt).getTime();
    const nextSplit = join(splitAt);
    const nextEnds = join(endsAt);
    const changed = nextSplit !== season.splitStartsAt || nextEnds !== season.endsAt;

    const submit = async (e) => {
        e.preventDefault();
        if (!splitAt.date || !endsAt.date) return errorAlert('날짜를 입력해주세요.');
        if (!window.confirm(`${season.name} 일정을 바꿀까요?\n스플릿 2: ${label(nextSplit)}\n시즌 종료: ${label(nextEnds)}`)) return;
        setSaving(true);
        try {
            apply(await updateSeasonSchedule({splitStartsAt: nextSplit, endsAt: nextEnds}));
            infoAlert('시즌 일정을 바꿨어요. 다음 정각부터 반영돼요.');
        } catch (err) {
            errorAlert(getErrorMessage(err, '일정을 바꾸지 못했어요.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card as="form" onSubmit={submit} className="flex flex-col gap-5 p-5">
            <div className="flex flex-col gap-1">
                <h3 className="text-base font-bold">{season.name} 일정</h3>
                <p className="text-xs text-fg-3">
                    시즌 시작 {season.startAt?.replaceAll('-', '.')} · 정각 단위로만 바꿀 수 있고, 스케줄러가 다음 정각 실행부터 반영해요.
                    다음 시즌 일정은 기본 규칙(운영: 매월 1일·16일 시작)으로 다시 정해져요.
                </p>
            </div>

            {settling ? (
                <p className="rounded-xl bg-up/10 px-4 py-3 text-sm font-semibold text-up">종료 시각이 지나 정산을 기다리는 중이라 바꿀 수 없어요.</p>
            ) : (
                <>
                    <div className="grid gap-4 md:grid-cols-2">
                        <div className="flex flex-col gap-1.5">
                            <span className="text-sm font-semibold">스플릿 2 시작 <span className="font-normal text-fg-3">(1,000만원 추가 지급)</span></span>
                            <HourInput name="스플릿 2 시작" value={splitAt} onChange={setSplitAt} disabled={splitStarted}/>
                            <span className="text-xs text-fg-3">
                                {splitStarted ? '이미 시작돼서 바꿀 수 없어요.' : `지금: ${label(season.splitStartsAt)}`}
                            </span>
                        </div>
                        <div className="flex flex-col gap-1.5">
                            <span className="text-sm font-semibold">시즌 종료 <span className="font-normal text-fg-3">(정산 · 거래 마감)</span></span>
                            <HourInput name="시즌 종료" value={endsAt} onChange={setEndsAt}/>
                            <span className="text-xs text-fg-3">지금: {label(season.endsAt)}</span>
                        </div>
                    </div>
                    <div className="flex justify-end gap-2">
                        <Button variant="secondary" disabled={!changed || saving} onClick={() => apply(season)}>되돌리기</Button>
                        <Button type="submit" disabled={!changed || saving}>{saving ? '저장 중…' : '일정 저장'}</Button>
                    </div>
                </>
            )}
        </Card>
    );
}
