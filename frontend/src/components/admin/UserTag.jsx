import {useAdminUser} from './adminUserContext.js';

/** "닉네임 #번호". 누르면 그 사용자의 상세 창이 열린다 */
export default function UserTag({id, name, className = ''}) {
    const {openUser} = useAdminUser();
    if (id == null) return <span className={className}>{name}</span>;
    return (
        <button
            type="button"
            onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                openUser(id);
            }}
            className={`inline-flex min-w-0 items-baseline gap-1 font-semibold hover:underline ${className}`}
        >
            <span className="truncate">{name}</span>
            <span className="num shrink-0 font-normal text-fg-3">#{id}</span>
        </button>
    );
}
