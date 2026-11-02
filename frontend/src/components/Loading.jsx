import React, { useEffect } from "react";
import { createPortal } from "react-dom";

/**
 * 로딩 스피너
 * - 기본: 화면 전체를 덮는 오버레이 (스크롤 잠금)
 * - inline: 해당 영역 안에만 표시
 */
export default function Loading({ inline = false }) {
    useEffect(() => {
        if (inline) return;
        // 로딩 중일 때 스크롤 방지
        document.body.style.overflow = "hidden";
        document.documentElement.style.overflow = "hidden";

        return () => {
            // 로딩 끝나면 복원
            document.body.style.overflow = "";
            document.documentElement.style.overflow = "";
        };
    }, [inline]);

    if (inline) {
        return (
            <div className="loading-non-overlay">
                <div className="loading-spinner"></div>
            </div>
        );
    }

    return createPortal(
        <div className="loading-overlay">
            <div className="loading-spinner"></div>
        </div>,
        document.body
    );
}
