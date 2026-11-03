const VARIANTS = {
    primary: 'bg-brand text-[#111827] hover:brightness-95',
    secondary: 'border border-line bg-surface text-fg hover:bg-surface-2',
    ghost: 'text-fg-2 hover:bg-surface-2 hover:text-fg',
    inverse: 'bg-fg text-bg hover:opacity-90',
    buy: 'bg-up text-white hover:brightness-95',
    sell: 'bg-down text-white hover:brightness-95',
};

const SIZES = {
    sm: 'h-9 rounded-[10px] px-3 text-sm',
    md: 'h-11 rounded-xl px-5 text-[15px]',
    lg: 'h-14 rounded-2xl px-6 text-[17px]',
};

/**
 * 버튼. as로 Link 등 다른 요소를 쓸 수 있다.
 * variant: primary | secondary | ghost | inverse | buy(매수) | sell(매도)
 */
export default function Button({as: Tag = 'button', variant = 'primary', size = 'md', className = '', children, ...rest}) {
    const typeProps = Tag === 'button' && !rest.type ? {type: 'button'} : {};
    return (
        <Tag
            className={`inline-flex items-center justify-center gap-1.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
            {...typeProps}
            {...rest}
        >
            {children}
        </Tag>
    );
}
