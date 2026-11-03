import {createContext, useContext} from 'react';

export const ToastContext = createContext(null);

// useToast 훅: { infoAlert, errorAlert, addToast }
export const useToast = () => {
    const context = useContext(ToastContext);
    if (!context) {
        throw new Error('useToast는 ToastProvider 내부에서 사용되어야 합니다.');
    }
    const {infoAlert, errorAlert, addToast} = context;
    return {infoAlert, errorAlert, addToast};
};
