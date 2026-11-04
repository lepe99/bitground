import {useEffect, useState} from "react";
import {useAuth} from "../auth/useAuth.js";
import {fetchCurrentRanking, fetchSeasonRanking} from "../api/rankApi.js";
import {fetchSeasons} from "../api/seasonApi.js";
import {useMediaQuery} from "../hooks/useMediaQuery.js";
import SegmentedTabs from "../components/ui/SegmentedTabs.jsx";
import RankPodium from "../components/rank/RankPodium.jsx";
import RankTable from "../components/rank/RankTable.jsx";
import MyPosition from "../components/rank/MyPosition.jsx";
import TierGuide from "../components/rank/TierGuide.jsx";
import UserRankSheet from "../components/rank/UserRankSheet.jsx";

const VIEWS = [
    {value: "current", label: "현재 시즌"},
    {value: "past", label: "지난 시즌"},
];

// 랭킹은 매시 정각에 다시 집계된다
const updatedText = (iso, now) => {
    if (!iso) return null;
    const at = new Date(iso);
    const next = new Date(at);
    next.setHours(at.getHours() + 1, 0, 0, 0);
    const minutes = Math.max(0, Math.ceil((next - now) / 60000));
    const hour = at.getHours();
    return `${at.getMonth() + 1}월 ${at.getDate()}일 ${hour < 12 ? "오전" : "오후"} ${hour % 12 || 12}시 기준 · 다음 갱신까지 ${minutes}분`;
};

export default function Rank() {
    const {user} = useAuth();
    const [view, setView] = useState("current");
    const [current, setCurrent] = useState(null); // null: 불러오는 중
    const [seasons, setSeasons] = useState([]);   // 지난(완료) 시즌
    const [pastId, setPastId] = useState(null);
    const [past, setPast] = useState({});         // seasonId → rankings
    const [selected, setSelected] = useState(null);
    // 마우스가 있으면 사진에 올려 티어 이력을 보고, 터치 화면은 눌러서 시트로 본다
    const canHover = useMediaQuery("(hover: hover) and (pointer: fine)");
    const onSelect = canHover ? undefined : setSelected;
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        fetchCurrentRanking()
            .then((list) => setCurrent(Array.isArray(list) ? list : []))
            .catch((error) => {
                console.error("실시간 랭킹 조회 실패:", error);
                setCurrent([]);
            });
        fetchSeasons()
            .then((list) => {
                const done = list.filter((s) => s.status === "COMPLETED");
                setSeasons(done);
                setPastId(done[0]?.id ?? null);
            })
            .catch((error) => console.error("시즌 목록 조회 실패:", error));
        const timer = setInterval(() => setNow(Date.now()), 30_000);
        return () => clearInterval(timer);
    }, []);

    useEffect(() => {
        if (view !== "past" || !pastId || past[pastId]) return;
        fetchSeasonRanking(pastId)
            .then((list) => setPast((p) => ({...p, [pastId]: Array.isArray(list) ? list : []})))
            .catch(() => setPast((p) => ({...p, [pastId]: []})));
    }, [view, pastId, past]);

    const rankings = view === "current" ? current : past[pastId] ?? null;
    const rows = [...(rankings ?? [])].sort((a, b) => a.ranks - b.ranks);
    const me = current?.find((r) => r.userId === user?.id);
    const season = view === "current" ? current?.[0]?.seasonName ?? "이번 시즌" : seasons.find((s) => s.id === pastId)?.name;

    return (
        <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-5 pt-4 pb-10 md:gap-6 md:px-8 md:pt-8 md:pb-14">
            <section className="flex flex-wrap items-end justify-between gap-3">
                <div className="flex flex-col gap-1.5">
                    <h1 className="text-xl font-extrabold tracking-[-0.02em] md:text-[28px]">{season} 랭킹</h1>
                    <span className="text-[13px] text-fg-3 md:text-sm">
                        {view === "current"
                            ? [updatedText(current?.[0]?.updatedAt, now), current ? `참가자 ${current.length}명` : null].filter(Boolean).join(" · ")
                            : "시즌 종료 시점 최종 순위"}
                    </span>
                </div>
                <div className="flex w-full flex-wrap items-center gap-2 md:w-auto">
                    <SegmentedTabs label="시즌 선택" items={VIEWS} value={view} onChange={setView} fill className="flex-1 md:flex-none"/>
                    {view === "past" && seasons.length > 0 && (
                        <select
                            aria-label="지난 시즌"
                            value={pastId ?? ""}
                            onChange={(e) => setPastId(Number(e.target.value))}
                            className="h-11 rounded-xl border border-line bg-surface px-3 text-sm font-semibold"
                        >
                            {seasons.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                        </select>
                    )}
                </div>
            </section>

            {/* 모바일: 내 위치를 맨 위에 */}
            {view === "current" && current && <div className="md:hidden"><MyPosition me={me} rankings={current}/></div>}

            {rankings === null ? (
                <p className="py-16 text-center text-sm text-fg-3">불러오는 중…</p>
            ) : view === "past" && seasons.length === 0 ? (
                <p className="py-16 text-center text-sm text-fg-3">아직 끝난 시즌이 없어요.</p>
            ) : rows.length === 0 ? (
                <p className="py-16 text-center text-sm text-fg-3">아직 순위에 오른 참가자가 없어요.</p>
            ) : (
                <>
                    <RankPodium top={rows.slice(0, 3)} onSelect={onSelect} participants={rows.length}/>
                    <section className="grid items-start gap-4 md:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)] md:gap-5">
                        <RankTable rows={rows.slice(3)} myUserId={view === "current" ? user?.id : null} onSelect={onSelect} participants={rows.length}/>
                        <aside className="flex flex-col gap-4 md:gap-5">
                            {view === "current" && <div className="max-md:hidden"><MyPosition me={me} rankings={current}/></div>}
                            <TierGuide myTier={me?.tier}/>
                        </aside>
                    </section>
                </>
            )}

            <UserRankSheet user={selected} onClose={() => setSelected(null)}/>
        </div>
    );
}
