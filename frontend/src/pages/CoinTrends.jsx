import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {fetchNews} from "../api/newsApi.js";
import {fetchAlertCoins, fetchCoinInsight, fetchTodayInsightSymbols, fetchWarningCoins} from "../api/coinApi.js";
import Card from "../components/ui/Card.jsx";
import SegmentedTabs from "../components/ui/SegmentedTabs.jsx";
import TopCoins from "../components/main/TopCoins.jsx";

// AI 분석 목록 맨 앞에 둘 종목 (나머지는 그날 AI가 고른 종목을 이름순으로 최대 5개)
const PINNED = ["MARKET_OVERALL", "KRW-BTC", "KRW-ETH", "KRW-SOL", "KRW-XRP"];
const NEWS_KEYWORDS = ["비트코인", "이더리움", "리플", "솔라나", "가상자산"];
const NEWS_PAGE_SIZE = 5;

// 네이버 뉴스 제목의 <b> 태그와 &quot; 같은 문자를 글자로 바꾼다
const plain = (html) => new DOMParser().parseFromString(html ?? "", "text/html").body.textContent ?? "";

// 분위기 라벨은 스케줄러가 등락률 규칙으로 정한다 (상승 빨강, 하락 파랑)
const MOOD_STYLE = {
    "강세": "bg-up/10 text-up",
    "약간 강세": "bg-up/10 text-up",
    "중립": "bg-surface-2 text-fg-2",
    "약간 약세": "bg-down/10 text-down",
    "약세": "bg-down/10 text-down",
};

function InsightList({title, items}) {
    if (!items?.length) return null;
    return (
        <section className="flex flex-col gap-1.5">
            <h3 className="text-[13px] font-bold text-fg-3">{title}</h3>
            <ul className="flex flex-col gap-1.5">
                {items.map((text, i) => (
                    <li key={i} className="flex gap-2 text-[15px] leading-relaxed text-fg-2">
                        <span className="mt-[9px] size-1.5 shrink-0 rounded-full bg-fg-3" aria-hidden="true"/>
                        <span>{text}</span>
                    </li>
                ))}
            </ul>
        </section>
    );
}

/** AI 분석 본문: 분위기 라벨·선정 이유, 요약, 근거, 지켜볼 점, 참고 기사 (예전 형식은 요약만) */
function InsightBody({insight}) {
    const date = new Date(insight.date).toLocaleDateString("ko-KR");
    return (
        <div className="flex flex-col gap-4">
            {insight.mood && (
                <div className="flex flex-wrap items-center gap-2">
                    <span className={`rounded-full px-2.5 py-1 text-[13px] font-bold ${MOOD_STYLE[insight.mood] ?? "bg-surface-2 text-fg-2"}`}>
                        최근 24시간 {insight.mood}
                    </span>
                    {insight.pickReason && insight.pickReason !== "주요 코인" && (
                        <span className="rounded-full border border-line px-2.5 py-1 text-xs font-semibold text-fg-2">{insight.pickReason}</span>
                    )}
                </div>
            )}
            <p className="whitespace-pre-wrap text-[15px] leading-[1.75] text-fg-2">{insight.insight}</p>
            <InsightList title="근거" items={insight.drivers}/>
            <InsightList title="지켜볼 점" items={insight.risks}/>
            {insight.sources?.length > 0 && (
                <section className="flex flex-col gap-1.5">
                    <h3 className="text-[13px] font-bold text-fg-3">참고 기사</h3>
                    <ul className="flex flex-col gap-1">
                        {insight.sources.map((s) => (
                            <li key={s.id} className="flex flex-wrap items-baseline gap-x-2 text-sm">
                                <a href={s.url} target="_blank" rel="noreferrer" className="text-fg-2 underline-offset-2 hover:text-fg hover:underline">{s.title}</a>
                                <span className="num text-xs text-fg-3">{s.publishedAt}</span>
                            </li>
                        ))}
                    </ul>
                </section>
            )}
            <p className="text-xs text-fg-3">
                <span className="num">{date} 0시</span> 기준 업비트 시세{insight.sources?.length > 0 ? "·뉴스" : ""}로 AI가 정리한 참고용 요약이며, 투자 권유가 아니에요.
            </p>
        </div>
    );
}

function AiInsightCard() {
    const [options, setOptions] = useState(null);
    const [symbol, setSymbol] = useState(null);
    const [insight, setInsight] = useState(undefined); // undefined: 불러오는 중, null: 없음

    useEffect(() => {
        fetchTodayInsightSymbols().then((list) => {
            const pinned = PINNED.map((s) => list.find((x) => x.symbol === s)).filter(Boolean);
            const others = list
                .filter((x) => !PINNED.includes(x.symbol))
                .sort((a, b) => a.koreanName.localeCompare(b.koreanName, "ko"))
                .slice(0, 5);
            const all = [...pinned, ...others];
            setOptions(all);
            setSymbol(all[0]?.symbol ?? null);
            if (!all.length) setInsight(null);
        });
    }, []);

    useEffect(() => {
        if (!symbol) return;
        let alive = true;
        setInsight(undefined);
        fetchCoinInsight(symbol).then((res) => alive && setInsight(res ?? null));
        return () => {
            alive = false;
        };
    }, [symbol]);

    const label = (o) => (o.symbol === "MARKET_OVERALL" ? "시장 전체" : o.koreanName);

    return (
        <Card className="flex flex-col gap-4 p-5 md:col-span-2 md:p-6">
            <div className="flex items-baseline justify-between gap-2">
                <h2 className="text-base font-bold md:text-[17px]">오늘의 AI 분석</h2>
                <span className="text-xs text-fg-3">매일 0시 갱신</span>
            </div>
            {options?.length > 0 && (
                <div role="tablist" aria-label="분석 종목" className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1">
                    {options.map((o) => (
                        <button
                            key={o.symbol}
                            type="button"
                            role="tab"
                            aria-selected={o.symbol === symbol}
                            onClick={() => setSymbol(o.symbol)}
                            className={`h-9 shrink-0 whitespace-nowrap rounded-full border px-3.5 text-[13px] font-semibold ${
                                o.symbol === symbol ? "border-fg bg-fg text-bg" : "border-line text-fg-2 hover:text-fg"
                            }`}
                        >
                            {label(o)}
                        </button>
                    ))}
                </div>
            )}
            {insight === undefined && options !== null && options.length > 0 ? (
                <p className="py-8 text-center text-sm text-fg-3">불러오는 중…</p>
            ) : insight ? (
                <InsightBody insight={insight}/>
            ) : (
                <p className="py-8 text-center text-sm text-fg-3">{options === null ? "불러오는 중…" : "오늘 분석이 아직 없어요."}</p>
            )}
        </Card>
    );
}

function CautionCard() {
    const [tab, setTab] = useState("warning");
    const [lists, setLists] = useState(null);

    useEffect(() => {
        Promise.all([fetchWarningCoins(), fetchAlertCoins()]).then(([warning, alert]) => setLists({warning, alert}));
    }, []);

    const items = lists?.[tab] ?? [];
    return (
        <Card className="flex flex-col gap-2 p-5 md:p-6">
            <div className="flex items-center justify-between gap-2 pb-2">
                <h2 className="text-base font-bold md:text-[17px]">주의 종목</h2>
                <SegmentedTabs
                    label="주의 종류"
                    items={[{value: "warning", label: "거래유의"}, {value: "alert", label: "투자주의"}]}
                    value={tab}
                    onChange={setTab}
                    className="[&>button]:h-8 [&>button]:px-3 [&>button]:text-xs"
                />
            </div>
            {lists === null && <p className="py-6 text-center text-sm text-fg-3">불러오는 중…</p>}
            {lists && items.length === 0 && <p className="py-6 text-center text-sm text-fg-3">지금은 {tab === "warning" ? "거래유의" : "투자주의"} 종목이 없어요.</p>}
            <ul>
                {items.map((c) => (
                    <li key={c.symbol}>
                        <Link to={`/trade?market=${c.symbol}`} className="flex items-center justify-between gap-3 py-2">
                            <span className="truncate text-sm font-semibold">{c.koreanName} <span className="font-normal text-fg-3">{c.symbol.replace("KRW-", "")}</span></span>
                            <span className={`shrink-0 rounded-md px-2 py-0.5 text-xs font-bold ${tab === "warning" ? "bg-up/10 text-up" : "bg-brand-soft text-brand-fg"}`}>
                                {tab === "warning" ? "유의" : "주의"}
                            </span>
                        </Link>
                    </li>
                ))}
            </ul>
        </Card>
    );
}

function NewsCard() {
    const [keyword, setKeyword] = useState(NEWS_KEYWORDS[0]);
    const [draft, setDraft] = useState("");
    const [page, setPage] = useState(1);
    const [news, setNews] = useState(null);
    const [totalPages, setTotalPages] = useState(1);

    useEffect(() => {
        let alive = true;
        setNews(null);
        fetchNews(keyword, page, NEWS_PAGE_SIZE).then((res) => {
            if (!alive) return;
            setNews(res.newsData ?? []);
            setTotalPages(res.totalPage ?? 1);
        });
        return () => {
            alive = false;
        };
    }, [keyword, page]);

    const search = (e) => {
        e.preventDefault();
        if (!draft.trim()) return;
        setKeyword(draft.trim());
        setPage(1);
    };

    return (
        <Card className="flex flex-col gap-3 p-5 md:col-span-2 md:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-base font-bold md:text-[17px]">최신 뉴스 <span className="font-normal text-fg-3">· {keyword}</span></h2>
                <form onSubmit={search} className="flex h-9 w-full items-center gap-2 rounded-[10px] bg-surface-2 px-3 text-fg-3 sm:w-60">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>
                    <label className="sr-only" htmlFor="news-q">뉴스 검색</label>
                    <input id="news-q" value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="키워드 검색" className="min-w-0 flex-1 text-[13px] text-fg outline-none placeholder:text-fg-3"/>
                </form>
            </div>
            <div className="-mx-1 flex gap-1.5 overflow-x-auto px-1">
                {NEWS_KEYWORDS.map((k) => (
                    <button
                        key={k}
                        type="button"
                        onClick={() => {
                            setKeyword(k);
                            setPage(1);
                        }}
                        aria-pressed={k === keyword}
                        className={`h-8 shrink-0 rounded-full px-3 text-xs font-semibold ${k === keyword ? "bg-brand-soft text-brand-fg" : "bg-surface-2 text-fg-2"}`}
                    >
                        {k}
                    </button>
                ))}
            </div>
            {news === null && <p className="py-10 text-center text-sm text-fg-3">불러오는 중…</p>}
            {news?.length === 0 && <p className="py-10 text-center text-sm text-fg-3">검색된 뉴스가 없어요.</p>}
            <ul>
                {news?.map((n) => (
                    <li key={n.link} className="border-t border-line first:border-t-0">
                        <a href={n.originallink || n.link} target="_blank" rel="noopener noreferrer" className="group flex flex-col gap-1 py-3">
                            <span className="text-[15px] font-semibold group-hover:underline">{plain(n.title)}</span>
                            <span className="line-clamp-2 text-[13px] leading-relaxed text-fg-3">{plain(n.description)}</span>
                            <span className="num text-xs text-fg-3">{new Date(n.pubDate).toLocaleString("ko-KR", {month: "long", day: "numeric", hour: "2-digit", minute: "2-digit"})}</span>
                        </a>
                    </li>
                ))}
            </ul>
            <div className="flex items-center justify-center gap-3 pt-1">
                <button type="button" onClick={() => setPage((p) => p - 1)} disabled={page <= 1} className="h-9 rounded-[10px] border border-line px-4 text-sm font-semibold text-fg-2 disabled:opacity-40">이전</button>
                <span className="num text-sm text-fg-3">{page} / {totalPages}</span>
                <button type="button" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages} className="h-9 rounded-[10px] border border-line px-4 text-sm font-semibold text-fg-2 disabled:opacity-40">다음</button>
            </div>
        </Card>
    );
}

/** 최신동향: AI 분석 · TOP5 · 주의 종목 · 뉴스 */
export default function CoinTrends() {
    return (
        <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-5 pt-4 pb-10 md:gap-5 md:px-8 md:pt-8 md:pb-14">
            <h1 className="text-xl font-extrabold md:text-[28px]">최신동향</h1>
            <div className="grid items-start gap-4 md:grid-cols-3 md:gap-5">
                <AiInsightCard/>
                <TopCoins/>
                <NewsCard/>
                <CautionCard/>
            </div>
        </div>
    );
}
