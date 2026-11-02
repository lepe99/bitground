import React, {useEffect} from 'react';
import {Navigate, Outlet} from 'react-router-dom';
import {useAuth} from '../auth/useAuth';
import Loading from "../components/Loading.jsx";

const ProtectedRoute = () => {
    const {checkAuthState, isLoggedIn, loading} = useAuth();

    useEffect(() => {
        // 보호된 라우트에서만 인증 상태 확인
        checkAuthState();
    }, [checkAuthState]);

    if (loading) return <Loading/>;
    if (!loading && !isLoggedIn) return <Navigate to="/login" replace/>;

    return <Outlet/>; // 자식 라우트들을 렌더링
};

export default ProtectedRoute;