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
            <div className="flex min-h-[100px] w-full items-center justify-center" role="status" aria-label="불러오는 중">
                <span className="size-10 animate-spin rounded-full border-4 border-line border-t-fg-2"/>
            </div>
        );
    }

    return createPortal(
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-bg/80" role="status" aria-label="불러오는 중">
            <span className="size-10 animate-spin rounded-full border-4 border-line border-t-fg-2"/>
        </div>,
        document.body
    );
}
