import React, {useState, useEffect, useCallback, useRef} from 'react';
import {AuthContext} from './AuthContext.js';
import {fetchMe, logout as logoutRequest} from '../api/authApi.js';

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
            const me = await fetchMe();
            if (me) {
                setUser(me);
                setIsLoggedIn(true);
            }
            return me;
        } catch (error) {
            console.warn('Not authenticated or error fetching user info:', error.response?.data?.detail || error.message);
            setUser(null);
            setIsLoggedIn(false);
            return null;
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


    // 서버 세션(쿠키)을 지우고 첫 화면으로. 여러 곳에서 동시에 불려도 한 번만 처리한다
    const loggingOutRef = useRef(false);
    const endSession = useCallback(async ({silent}) => {
        if (loggingOutRef.current) return;
        loggingOutRef.current = true;
        try {
            await logoutRequest();
        } catch (error) {
            console.error('Logout failed:', error);
            // 사용자가 누른 로그아웃이 실패하면 알린다 (세션 만료로 인한 자동 로그아웃은 이미 끝난 세션이라 알리지 않는다)
            if (!silent) alert("로그아웃 중 오류가 발생했습니다. 다시 시도해주세요.");
        } finally {
            setUser(null);
            setIsLoggedIn(false);
            // 쿠키는 백엔드에서 HttpOnly로 제거하므로 프론트에서 할 일 없음
            window.location.href = '/?logout=true';
        }
    }, []);

    const logout = useCallback(() => endSession({silent: false}), [endSession]);

    useEffect(() => {
        // 리프레시 토큰이 만료·폐기되어 재발급이 거절되면 axios 인터셉터가 보내는 이벤트
        const handleForceLogout = () => {
            if (!isLoggedIn) return;
            void endSession({silent: true});
        };
        window.addEventListener('forceLogout', handleForceLogout);
        return () => {
            window.removeEventListener('forceLogout', handleForceLogout);
        };
    }, [isLoggedIn, endSession]);


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