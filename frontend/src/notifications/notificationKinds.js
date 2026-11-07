// 알림 종류별 아이콘·색·이동할 화면 (알림 목록과 실시간 토스트가 함께 쓴다)

// 알림 종류별 이동할 화면 (댓글·문의 알림은 그 게시글)
const TARGETS = {
    NOTICE: '/notice',
    ORDER_EXECUTION: '/investments',
    SEASON_UPDATE: '/rank',
};

export const targetOf = ({messageType, postId}) => ((messageType === 'COMMENT' || messageType === 'INQUIRY') && postId
    ? `/community/${postId}` : TARGETS[messageType]);

/** 알림 종류별 아이콘·색. 체결은 매수(빨강)·매도(파랑), 매수·매도가 섞인 묶음은 기본색 */
export function kindOf({messageType, message = ''}) {
    switch (messageType) {
        case 'ORDER_EXECUTION': {
            const sell = message.includes('매도');
            const buy = message.includes('매수');
            if (sell && buy) return {icon: 'trade', tone: 'bg-surface-2 text-fg'};
            return sell ? {icon: 'trade', tone: 'bg-down/10 text-down'} : {icon: 'trade', tone: 'bg-up/10 text-up'};
        }
        case 'COMMENT':
            return {icon: 'comment', tone: 'bg-brand-soft text-brand-fg'};
        case 'INQUIRY':
            return {icon: 'inquiry', tone: 'bg-brand-soft text-brand-fg'};
        case 'NOTICE':
            return {icon: 'notice', tone: 'bg-surface-2 text-fg'};
        default:
            return {icon: 'season', tone: 'bg-surface-2 text-fg'};
    }
}
