import {useEffect, useRef} from 'react';

/** 모바일은 아래에서 올라오는 시트, PC는 가운데 창. Esc나 바깥을 누르면 닫힌다 */
export default function Sheet({open, onClose, title, children}) {
    const ref = useRef(null);

    useEffect(() => {
        const dialog = ref.current;
        if (!dialog) return;
        if (open && !dialog.open) dialog.showModal();
        if (!open && dialog.open) dialog.close();
    }, [open]);

    return (
        <dialog
            ref={ref}
            aria-label={title}
            onClose={onClose}
            onClick={(e) => {
                if (e.target === ref.current) onClose();
            }}
            className="rd fixed inset-x-0 top-auto bottom-0 m-0 border-0 text-fg max-h-[90dvh] w-full max-w-none overflow-y-auto rounded-t-3xl bg-bg p-0 pb-[env(safe-area-inset-bottom)] backdrop:bg-black/50 md:inset-0 md:m-auto md:h-fit md:max-w-[420px] md:rounded-3xl md:pb-0"
        >
            <div className="flex items-center justify-between px-5 pt-4 pb-2">
                <span className="text-base font-bold">{title}</span>
                <button type="button" onClick={onClose} aria-label="닫기" className="flex size-9 items-center justify-center rounded-full text-fg-2 hover:bg-surface-2">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
                </button>
            </div>
            <div className="px-3 pb-4">{open && children}</div>
        </dialog>
    );
}
