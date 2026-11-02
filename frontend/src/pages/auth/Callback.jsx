import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import {useToast} from "../../components/Toast.jsx";
import Loading from "../../components/Loading.jsx";

const Callback = () => {
    const navigate = useNavigate();
    const { checkAuthState, user } = useAuth();
    const { infoAlert } = useToast();
    const hasProcessed = useRef(false);
    const hasShownWelcome = useRef(false);

    useEffect(() => {
        // 컴포넌트 마운트 시 초기화
        hasProcessed.current = false;
        hasShownWelcome.current = false;
    }, []);

    useEffect(() => {
        if (hasProcessed.current) return;

        hasProcessed.current = true;

        checkAuthState()
            .then(() => {
                localStorage.setItem('authState', 'true');
                navigate('/');
            })
            .catch((error) => {
                console.error('Error processing auth callback:', error);
                navigate('/login');
            });
    }, [checkAuthState, navigate]);

    // 사용자 정보가 로드된 후 환영 메시지 표시 (한 번만)
    useEffect(() => {
        if (user && user.name && hasProcessed.current && !hasShownWelcome.current) {
            hasShownWelcome.current = true;
            infoAlert(`${user.name}님, 환영합니다!`);
        }
    }, [user, infoAlert]);

    return (
        <div>
            <Loading/>
        </div>
    );
};

export default Callback;