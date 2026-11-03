/**
 * 알약 모양 탭 (시즌 선택, 보유자산/거래내역 등)
 * @param items [{value, label, count?}] count가 있으면 숫자 배지를 붙인다
 * @param fill true면 칸을 같은 너비로 채운다
 */
export default function SegmentedTabs({items, value, onChange, label, fill = false, className = ''}) {
    return (
        <div role="tablist" aria-label={label} className={`flex gap-1 rounded-xl bg-surface-2 p-1 ${className}`}>
            {items.map((item) => {
                const selected = item.value === value;
                return (
                    <button
                        key={item.value}
                        type="button"
                        role="tab"
                        aria-selected={selected}
                        onClick={() => onChange(item.value)}
                        className={`inline-flex h-9 items-center justify-center gap-1.5 rounded-[9px] px-4 text-sm transition ${fill ? 'flex-1' : ''} ${
                            selected ? 'bg-surface font-bold text-fg shadow-sm' : 'font-semibold text-fg-3 hover:text-fg-2'
                        }`}
                    >
                        {item.label}
                        {item.count > 0 && (
                            <span className="num inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-brand px-1.5 text-[11px] font-extrabold text-[#111827]">
                                {item.count}
                            </span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}
