import {useAuth} from '../auth/useAuth';
import '../styles/Login.css';
import {CiLock} from "react-icons/ci";
import {useToast} from "../components/Toast.jsx";
import {useEffect} from "react";

// warning: 사용자 정보 표시 로직은 테스트용이기에 추후 삭제해야 합니다.
export default function Main() {
    const {login} = useAuth();
    const {errorAlert} = useToast();

    useEffect(() => {
        // url 파라미터에 에러 메시지가 있다면 표시
        const errorMessage = new URLSearchParams(window.location.search).get('error');
        if (errorMessage) {
            errorAlert("로그인 중 오류가 발생했습니다. 다시 시도해주세요.");
            console.error('Auth error:', errorMessage);
        }
    }, []);


    return (
        <div className={"container"}>
            <div className="login-container">
                <div className={"login-title"}>
                    <p className={"text-login"}>로그인</p>
                    <p className={"text-login-check"}>방문하신 사이트의 주소가 아래와 일치하는지 확인하세요.</p>
                    <div className={"text-addr"}><CiLock/>https://www.bitground.kr</div>
                </div>
                <div className={"login-content"}>
                    <button onClick={() => login('kakao')} className={"kakao-btn"}>카카오 로그인</button>
                    <button onClick={() => login('naver')} className={"naver-btn"}>네이버 로그인</button>
                    <button onClick={() => login('google')} className={"google-btn"}>구글 로그인</button>
                </div>
            </div>
        </div>
    );

}