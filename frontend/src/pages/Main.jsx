import '../styles/Main.css';
import MarketChart from "../components/main/MarketChart.jsx";
import AltChart from "../components/main/AltChart.jsx";
import MainRanking from "../components/main/MainRanking.jsx";
import {useToast} from "../components/Toast.jsx";
import {useEffect} from "react";
import {useNavigate} from "react-router-dom";



export default function Main() {

    const {infoAlert} = useToast();
    const navigate = useNavigate();

    useEffect(() => {
        // url 파라미터에 로그아웃 메시지가 있다면 표시
        const logoutMessage = new URLSearchParams(window.location.search).get('logout');
        if (logoutMessage) {
            infoAlert("로그아웃 되었습니다.");
            navigate('/');
        }
    }, []);


    return (
        <div className="main-container">
            <div className={"banner-container"}>
                <div className={"text-banner"}>Invest everything</div>
                <div className={"mask-wrapper"}>
                    <div className={"banner-back"}>
                        <div className={"ipad"}>
                            <div className={"ipad-screen"}></div>
                        </div>
                    </div>
                </div>
            </div>
            <div className={"market-price-container"}>
                <MarketChart />
                <AltChart />
            </div>
            <div className={"rank-container"}>
                <MainRanking />
            </div>
        </div>
    );
}