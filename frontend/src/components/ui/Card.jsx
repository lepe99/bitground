/**
 * 카드. 모바일은 테두리 없이 바탕색 차이로, PC는 얇은 테두리로 구분한다.
 */
export default function Card({as = 'section', className = '', children, ...rest}) {
    const Tag = as;
    return (
        <Tag className={`rounded-[20px] bg-surface md:border md:border-line ${className}`} {...rest}>
            {children}
        </Tag>
    );
}
