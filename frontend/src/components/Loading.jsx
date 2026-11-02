import React, { useEffect } from "react";
import { createPortal } from "react-dom";

export default function Loading() {
    useEffect(() => {
        // 로딩 중일 때 스크롤 방지
        document.body.style.overflow = "hidden";
        document.documentElement.style.overflow = "hidden";

        return () => {
            // 로딩 끝나면 복원
            document.body.style.overflow = "";
            document.documentElement.style.overflow = "";
        };
    }, []);

    return createPortal(
        <div className="loading-overlay">
            <div className="loading-spinner"></div>
        </div>,
        document.body
    );
}