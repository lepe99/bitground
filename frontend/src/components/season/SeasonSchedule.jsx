import {useEffect, useState} from 'react';
import Card from '../ui/Card.jsx';
import {fetchCurrentSeason} from '../../api/seasonApi.js';

const TICK_MS = 30_000;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 서버가 주는 시각은 한국 시각(시간대 없음). 화면과 서버 시각을 같은 방식으로 읽어 차이만 쓰므로 PC 시간대와 상관없다
const parse = (s) => (s ? new Date(s) : null);

const when = (d) => `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${d.getHours()}시`;

const left = (ms) => {
    const minutes = Math.max(0, Math.floor(ms / 60_000));
    const days = Math.floor(minutes / 1440);
    const hours = Math.floor((minutes % 1440) / 60);
    if (days > 0) return `${days}일 ${hours}시간`;
    if (hours > 0) return `${hours}시간 ${minutes % 60}분`;
    return `${Math.max(1, minutes)}분`;
};

/** 진행 중인 시즌 정보와 서버 시각에 맞춘 현재 시각 (30초마다 갱신, 시즌이 끝나면 새 시즌을 다시 읽는다) */
function useCurrentSeason() {
    const [season, setSeason] = useState(null);
    const [offset, setOffset] = useState(0); // 서버 시각 - 내 PC 시각
    const [now, setNow] = useState(() => Date.now());
    const [version, setVersion] = useState(0);

    useEffect(() => {
        let alive = true;
        fetchCurrentSeason()
            .then((s) => {
                if (!alive) return;
                setSeason(s);
                setOffset(parse(s.now).getTime() - Date.now());
            })
            .catch(() => alive && setSeason(false));
        return () => {
            alive = false;
        };
    }, [version]);

    useEffect(() => {
        const timer = setInterval(() => setNow(Date.now()), TICK_MS);
        return () => clearInterval(timer);
    }, []);

    const current = now + offset;
    const endsAt = parse(season?.endsAt)?.getTime();
    // 정산이 끝날 즈음(1분 뒤) 새 시즌을 다시 읽는다
    useEffect(() => {
        if (!endsAt) return;
        const timer = setTimeout(() => setVersion((v) => v + 1), Math.max(endsAt - current, 0) + 60_000);
        return () => clearTimeout(timer);
    }, [endsAt]); // eslint-disable-line react-hooks/exhaustive-deps

    return {season, now: current};
}

function Milestone({label, at, note, now, done}) {
    return (
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="text-xs text-fg-3">{label}</span>
            <span className={`num text-lg font-extrabold md:text-xl ${done ? 'text-fg-3' : ''}`}>
                {done ? '시작됨' : `${left(at.getTime() - now)} 남음`}
            </span>
            <span className="num text-xs text-fg-2">{when(at)}</span>
            <span className="text-xs text-fg-3">{note}</span>
        </div>
    );
}

/**
 * 진행 중인 시즌 일정: 스플릿 2 시작(추가 자금)·시즌 종료(정산)까지 남은 시간과 진행 막대
 * 홈과 랭킹 화면 위쪽에 둔다
 */
export default function SeasonSchedule({className = ''}) {
    const {season, now} = useCurrentSeason();
    if (!season) return null;

    const start = parse(season.startAt + 'T00:00:00');
    const split = parse(season.splitStartsAt);
    const end = parse(season.endsAt);
    if (!start || !split || !end) return null;

    const total = end - start;
    const ratio = (t) => Math.min(100, Math.max(0, ((t - start) / total) * 100));
    const pos = (t) => `${ratio(t)}%`;
    // 스플릿 표시가 양 끝에 가까우면 '시작'·'종료' 글자와 겹치므로 그쪽 글자를 뺀다
    const splitRatio = ratio(split);
    const inSplit2 = now >= split.getTime();

    return (
        <Card className={`flex flex-col gap-4 p-5 md:flex-row md:items-center md:gap-8 md:p-6 ${className}`}>
            <div className="flex min-w-0 flex-col gap-3 md:flex-[1.3]">
                <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                    <h2 className="text-base font-bold md:text-[17px]">{season.name}</h2>
                    <span className="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-bold text-brand-fg">
                        스플릿 {inSplit2 ? 2 : 1} 진행 중
                    </span>
                </div>
                <div className="flex flex-col gap-1.5">
                    <div
                        className="relative h-2 rounded-full bg-surface-2"
                        role="img"
                        aria-label={`시즌 진행 ${pos(now)}, 스플릿 2는 ${when(split)}, 종료는 ${when(end)}`}
                    >
                        <div className="absolute inset-y-0 left-0 rounded-full bg-brand" style={{width: pos(now)}}/>
                        <span className="absolute -top-1 h-4 w-0.5 -translate-x-1/2 rounded bg-fg-3" style={{left: pos(split)}} aria-hidden="true"/>
                    </div>
                    <div className="relative h-4 text-[11px] text-fg-3" aria-hidden="true">
                        {splitRatio > 15 && <span className="absolute left-0">시작</span>}
                        <span
                            className={`absolute whitespace-nowrap ${splitRatio > 85 ? '-translate-x-full' : splitRatio < 15 ? '' : '-translate-x-1/2'}`}
                            style={{left: pos(split)}}
                        >
                            스플릿 2
                        </span>
                        {splitRatio < 85 && <span className="absolute right-0">종료</span>}
                    </div>
                </div>
            </div>
            <div className="flex gap-4 border-t border-line pt-4 md:flex-1 md:border-t-0 md:border-l md:pt-0 md:pl-8">
                <Milestone label="스플릿 2 시작" at={split} note="1,000만원 추가 지급" now={now} done={inSplit2}/>
                <Milestone label="시즌 종료" at={end} note="자산 초기화 · 티어 확정" now={now}/>
            </div>
        </Card>
    );
}
