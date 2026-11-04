import {useEffect, useState} from "react";
import {useNavigate, useSearchParams} from "react-router-dom";
import {useToast} from "../components/toastContext.js";
import {useAuth} from "../auth/useAuth.js";
import {fetchPublicRanking} from "../api/rankApi.js";
import Hero from "../components/main/Hero.jsx";
import MySummary from "../components/main/MySummary.jsx";
import LivePrices from "../components/main/LivePrices.jsx";
import MarketIndexCard from "../components/main/MarketIndexCard.jsx";
import SeasonSchedule from "../components/season/SeasonSchedule.jsx";
import TopCoins from "../components/main/TopCoins.jsx";
import SeasonRankingPreview from "../components/main/SeasonRankingPreview.jsx";

export default function Main() {
    const {infoAlert} = useToast();
    const {isLoggedIn} = useAuth();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const loggedOut = searchParams.get('logout');
    const [ranking, setRanking] = useState(undefined); // undefined: 불러오는 중, null: 실패

    // 로그아웃 후 /?logout=true 로 돌아오면 안내하고 주소를 정리한다
    useEffect(() => {
        if (!loggedOut) return;
        infoAlert("로그아웃 되었습니다.");
        navigate('/', {replace: true});
    }, [loggedOut, infoAlert, navigate]);

    useEffect(() => {
        fetchPublicRanking()
            .then(setRanking)
            .catch((error) => {
                console.error("랭킹 미리보기 조회 실패:", error);
                setRanking(null);
            });
    }, []);

    return (
        <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-5 pt-4 pb-10 md:gap-8 md:px-8 md:pt-14 md:pb-16">
            <section className="flex flex-wrap items-center gap-6 md:gap-12">
                <div className="min-w-0 flex-[1_1_420px]">
                    {isLoggedIn ? <MySummary/> : <Hero seasonName={ranking?.seasonName}/>}
                </div>
                <div className="min-w-0 flex-[1_1_520px]">
                    <LivePrices/>
                </div>
            </section>

            <SeasonSchedule/>

            <section className="grid gap-4 md:grid-cols-[repeat(auto-fit,minmax(300px,1fr))] md:gap-5">
                <MarketIndexCard/>
                <TopCoins/>
                <SeasonRankingPreview ranking={ranking}/>
            </section>
        </div>
    );
}
