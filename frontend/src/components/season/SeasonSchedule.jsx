import Card from '../ui/Card.jsx';
import {SPLIT_CASH_LABEL} from '../../constants/season.js';
import {parseServerTime as parse, useCurrentSeason} from '../../hooks/useCurrentSeason.js';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const when = (d) => `${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAYS[d.getDay()]}) ${d.getHours()}시`;

const left = (ms) => {
    const minutes = Math.max(0, Math.floor(ms / 60_000));
    const days = Math.floor(minutes / 1440);
    const hours = Math.floor((minutes % 1440) / 60);
    if (days > 0) return `${days}일 ${hours}시간`;
    if (hours > 0) return `${hours}시간 ${minutes % 60}분`;
    return `${Math.max(1, minutes)}분`;
};

const short = (d) => `${d.getMonth() + 1}/${d.getDate()} ${d.getHours()}시`;

/**
 * 시즌의 한 단계(스플릿). 두 단계를 같은 너비로 나란히 두고, 단계마다 지난 시간만큼 채운다
 * (시간 비례 막대 하나는 스플릿 길이가 다르면 표시가 한쪽에 몰리고, 어디가 지금인지 읽기 어려웠다)
 */
function Phase({name, from, to, now}) {
    const done = now >= to.getTime();
    const current = !done && now >= from.getTime();
    const fill = done ? 100 : current ? ((now - from) / (to - from)) * 100 : 0;
    const status = done ? '완료' : current ? '진행 중' : '예정';

    return (
        <li className="flex min-w-0 flex-col gap-1.5">
            <div className="flex items-baseline justify-between gap-2 text-xs">
                <span className={`font-bold ${current ? 'text-fg' : 'text-fg-3'}`}>{name}</span>
                <span className={current ? 'font-semibold text-brand-fg' : 'text-fg-3'}>{status}</span>
            </div>
            <div
                className={`h-2 overflow-hidden rounded-full ${current ? 'bg-brand-soft' : 'bg-surface-2'}`}
                role="progressbar"
                aria-label={`${name} ${status}`}
                aria-valuenow={Math.round(fill)}
                aria-valuemin={0}
                aria-valuemax={100}
            >
                <div className={`h-full rounded-full ${done ? 'bg-fg-3/40' : 'bg-brand'}`} style={{width: `${fill}%`}}/>
            </div>
            <span className="num truncate text-[11px] text-fg-3">{short(from)} ~ {short(to)}</span>
        </li>
    );
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
    const {season, now, closed} = useCurrentSeason();
    if (!season) return null;

    const start = parse(season.startAt + 'T00:00:00');
    const split = parse(season.splitStartsAt);
    const end = parse(season.endsAt);
    if (!start || !split || !end) return null;

    const inSplit2 = now >= split.getTime();
    const phases = [
        {name: '스플릿 1', from: start, to: split},
        {name: '스플릿 2', from: split, to: end},
    ];

    return (
        <Card className={`flex flex-col gap-4 p-5 md:flex-row md:items-center md:gap-8 md:p-6 ${className}`}>
            <div className="flex min-w-0 flex-col gap-3 md:flex-[1.3]">
                <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                    <h2 className="text-base font-bold md:text-[17px]">{season.name}</h2>
                    {closed ? (
                        <span className="rounded-full bg-up/10 px-2 py-0.5 text-xs font-bold text-up">시즌 정산 중 · 거래 마감</span>
                    ) : (
                        <span className="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-bold text-brand-fg">
                            스플릿 {inSplit2 ? 2 : 1} 진행 중
                        </span>
                    )}
                </div>
                <ol className="grid grid-cols-2 gap-2" aria-label="시즌 진행 단계">
                    {phases.map((p) => <Phase key={p.name} {...p} now={now}/>)}
                </ol>
            </div>
            <div className="flex gap-4 border-t border-line pt-4 md:flex-1 md:border-t-0 md:border-l md:pt-0 md:pl-8">
                <Milestone label="스플릿 2 시작" at={split} note={`${SPLIT_CASH_LABEL} 추가 지급`} now={now} done={inSplit2}/>
                <Milestone label="시즌 종료" at={end} note="자산 초기화 · 티어 확정" now={now}/>
            </div>
        </Card>
    );
}
