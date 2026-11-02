import React, {useState, useEffect, useCallback} from 'react';
import {AuthContext} from './AuthContext.js';
import api from '../api/axiosConfig.js';

export const AuthProvider = ({children}) => {
    const [isLoggedIn, setIsLoggedIn] = useState(false);
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true); // 초기 인증 상태 확인 로딩

    // 초기 렌더링 시 사용자 인증 상태 확인
    useEffect(() => {
        const authState = localStorage.getItem('authState');
        if (authState === 'true') {
            setIsLoggedIn(true);
        }
    }, []);

    // 보호된 경로 접근 시 사용자 인증 상태 확인
    const checkAuthState = useCallback(async () => {
        setLoading(true);

        try {
            // 백엔드에 현재 사용자 정보를 요청하는 API (쿠키에 유효한 AT가 있다면 성공)
            const response = await api.get('/users/me');
            if (response.data) {
                setUser(response.data.user);
                setIsLoggedIn(true);
            }
        } catch (error) {
            console.warn('Not authenticated or error fetching user info:', error.response?.data?.message || error.message);
            setUser(null);
            setIsLoggedIn(false);
            // 401이 아닌 다른 네트워크 오류일 수도 있으므로, 무조건 로그아웃 시키기보다는 상태만 업데이트
        } finally {
            setLoading(false);
        }
    }, []);

    const login = useCallback((provider) => {
        window.location.href = `${import.meta.env.VITE_API_URL}/oauth2/authorization/${provider}`;
    }, []);


    const logout = useCallback(async () => {
        localStorage.removeItem('authState'); // 로컬 스토리지에 저장된 인증 상태 제거
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