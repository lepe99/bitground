import React, {useEffect} from 'react';
import {Navigate, Outlet, useLocation} from 'react-router-dom';
import {useAuth} from '../auth/useAuth';
import Loading from "../components/Loading.jsx";

const ProtectedRoute = () => {
    const {checkAuthState, isLoggedIn, loading, user} = useAuth();
    const {pathname} = useLocation();

    useEffect(() => {
        // 보호된 라우트에서만 인증 상태 확인
        checkAuthState();
    }, [checkAuthState]);

    if (loading) return <Loading/>;
    if (!loading && !isLoggedIn) return <Navigate to="/login" replace/>;
    // 처음 가입한 사용자는 프로필 확인 화면을 먼저 마친다
    if (user && !user.onboarded && pathname !== "/welcome") return <Navigate to="/welcome" replace/>;

    return <Outlet/>; // 자식 라우트들을 렌더링
};

export default ProtectedRoute;
