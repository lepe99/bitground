import React, {useState, useEffect, useCallback, useRef} from 'react';
import {AuthContext} from './AuthContext.js';
import api from '../api/axiosConfig.js';

export const AuthProvider = ({children}) => {
    const [isLoggedIn, setIsLoggedIn] = useState(false);
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true); // 최초 인증 상태 확인 중
    const checkedRef = useRef(false);

    // 로그인 여부는 HttpOnly 쿠키 기준이므로 서버(/users/me)에 물어서 정하고 메모리에만 둔다.
    // (기존: localStorage 플래그로 판단해, 쿠키가 만료돼도 로그인된 것처럼 보이다가 강제 로그아웃됨)
    // 최초 확인 때만 loading을 켜고, 이후(보호된 경로 이동 시 재확인)에는 화면을 가리지 않는다.
    const checkAuthState = useCallback(async () => {
        if (!checkedRef.current) setLoading(true);

        try {
            // 백엔드에 현재 사용자 정보를 요청하는 API (쿠키에 유효한 AT가 있다면 성공)
            const response = await api.get('/users/me');
            if (response.data) {
                setUser(response.data);
                setIsLoggedIn(true);
            }
        } catch (error) {
            console.warn('Not authenticated or error fetching user info:', error.response?.data?.detail || error.message);
            setUser(null);
            setIsLoggedIn(false);
            // 401이 아닌 다른 네트워크 오류일 수도 있으므로, 무조건 로그아웃 시키기보다는 상태만 업데이트
        } finally {
            checkedRef.current = true;
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void checkAuthState();
    }, [checkAuthState]);

    const login = useCallback((provider) => {
        window.location.href = `${import.meta.env.VITE_API_URL}/oauth2/authorization/${provider}`;
    }, []);


    const logout = useCallback(async () => {
        try {
            await api.post('/auth/logout');
        } catch (error) {
            alert("로그아웃 중 오류가 발생했습니다. 다시 시도해주세요.");
            console.error('Logout failed:', error);
            // 실패하더라도 프론트엔드 상태는 로그아웃 처리
        } finally {
            setUser(null);
            setIsLoggedIn(false);
            // 쿠키는 백엔드에서 HttpOnly로 제거하므로 프론트에서 할 일 없음

            // 필요시 로그인 페이지로 리디렉션
            window.location.href = '/?logout=true';
        }
    }, []);

    useEffect(() => {
        // Axios 인터셉터에서 발생하는 강제 로그아웃 이벤트 리스너 (선택적 고급 처리)
        const handleForceLogout = () => {
            if (!isLoggedIn) return;
            console.log("Force logout event received");
            void logout();
        };
        window.addEventListener('forceLogout', handleForceLogout);
        return () => {
            window.removeEventListener('forceLogout', handleForceLogout);
        };

    }, [isLoggedIn, logout]);


    const value = {
        isLoggedIn,
        user,
        login,
        logout,
        loading,
        checkAuthState
    }

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};