import {useEffect, useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '../../auth/useAuth';
import {useToast} from "../../components/toastContext.js";
import Loading from "../../components/Loading.jsx";

/** 소셜 로그인 후 돌아오는 화면. 처음 가입한 사용자는 프로필 확인 화면으로, 나머지는 홈으로 보낸다 */
const Callback = () => {
    const navigate = useNavigate();
    const {checkAuthState} = useAuth();
    const {infoAlert} = useToast();
    const handled = useRef(false);

    useEffect(() => {
        if (handled.current) return;
        handled.current = true;

        checkAuthState().then((me) => {
            if (!me) {
                navigate('/login', {replace: true});
            } else if (!me.onboarded) {
                navigate('/welcome', {replace: true}); // 환영 인사는 프로필을 확인한 뒤에
            } else {
                infoAlert(`${me.name}님, 환영합니다!`);
                navigate('/', {replace: true});
            }
        });
    }, [checkAuthState, navigate, infoAlert]);

    return <Loading/>;
};

export default Callback;
