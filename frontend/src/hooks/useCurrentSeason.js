import {useEffect, useState} from 'react';
import {fetchCurrentSeason} from '../api/seasonApi.js';

const TICK_MS = 30_000;
// 정산이 오래 걸릴 때 새 시즌이 열렸는지 다시 확인하는 간격
const SETTLING_POLL_MS = 30_000;
// 이 시간 안에 끝나면 '곧 거래 마감'을 알린다
export const CLOSING_SOON_MS = 10 * 60_000;

// 서버가 주는 시각은 한국 시각(시간대 없음). 화면과 서버 시각을 같은 방식으로 읽어 차이만 쓰므로 PC 시간대와 상관없다
export const parseServerTime = (s) => (s ? new Date(s) : null);

/**
 * 진행 중인 시즌과 서버 시각에 맞춘 현재 시각 (30초마다 갱신)
 * - closed: 시즌 종료 시각이 지나 정산 중 (서버도 이때 주문을 받지 않는다). 새 시즌이 열릴 때까지 30초마다 다시 읽는다
 * - closingSoon: 10분 안에 거래 마감
 */
export function useCurrentSeason() {
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
                setOffset(parseServerTime(s.now).getTime() - Date.now());
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
    const endsAt = parseServerTime(season?.endsAt)?.getTime();
    const closed = Boolean(endsAt && current >= endsAt);
    const closingSoon = Boolean(endsAt && !closed && endsAt - current <= CLOSING_SOON_MS);

    // 종료 시각이 되면 바로 '정산 중'으로 바꾸고, 정산 중에는 새 시즌이 열렸는지 주기적으로 다시 읽는다
    useEffect(() => {
        if (!endsAt) return;
        const wait = closed ? SETTLING_POLL_MS : Math.max(endsAt - current, 0) + 1_000;
        const timer = setTimeout(() => {
            setNow(Date.now());
            if (closed) setVersion((v) => v + 1);
        }, wait);
        return () => clearTimeout(timer);
    }, [endsAt, closed, version]); // eslint-disable-line react-hooks/exhaustive-deps

    return {season, now: current, closed, closingSoon, endsAt};
}
