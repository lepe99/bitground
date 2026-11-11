// 비슷한 알림 묶기 (알림 목록과 실시간 토스트가 함께 쓴다)
// 알림: {id, messageType, message, postId, isRead, createdAt}. 목록은 최신순

/** 저장된 문구의 첫 줄은 제목, 나머지는 본문 */
export function splitMessage(message = '') {
    const [title, ...rest] = message.split('\n');
    return {title, body: rest.join('\n').trim()};
}

/**
 * 같은 묶음이 되는 기준: 같은 글의 댓글·문의 알림, 예약 주문 체결. 공지·시즌은 묶지 않는다.
 * 거래 알림 중 상장 폐지 정리·취소 안내는 체결과 문구가 달라 묶지 않는다
 */
export function groupKey({messageType, postId, message = ''}) {
    if ((messageType === 'COMMENT' || messageType === 'INQUIRY') && postId) return `post:${postId}`;
    // 지정가 체결 알림 (수수료 도입 전에 저장된 알림은 '예약'으로 시작)
    if (messageType === 'ORDER_EXECUTION' && (message.startsWith('지정가') || message.startsWith('예약'))) return 'order';
    return null;
}

/** 묶을 수 있는 알림끼리 모은다 (순서는 각 묶음의 가장 최근 알림 기준) */
export function groupNotifications(items) {
    const groups = [];
    const byKey = new Map();
    for (const item of items) {
        const key = groupKey(item);
        const existing = key && byKey.get(key);
        if (existing) {
            existing.items.push(item);
            continue;
        }
        const group = {key: key ?? `one:${item.id}`, items: [item]};
        if (key) byKey.set(key, group);
        groups.push(group);
    }
    return groups;
}

const unique = (values) => [...new Set(values.filter(Boolean))];

/** 묶음의 제목·본문. 하나면 원래 문구, 여럿이면 "○○님 외 1명이 댓글 3개를 남겼어요." 처럼 합친다 */
export function summarize(items) {
    const latest = items[0];
    if (items.length === 1) return splitMessage(latest.message);
    const n = items.length;

    if (latest.messageType === 'ORDER_EXECUTION') {
        const buys = items.filter((i) => i.message.includes('매수')).length;
        const sells = items.filter((i) => i.message.includes('매도')).length;
        const coins = unique(items.map((i) => i.message.match(/수량 : [\d.]+ (\S+)/)?.[1]));
        const parts = [buys && `매수 ${buys}건`, sells && `매도 ${sells}건`, coins.join(', ')].filter(Boolean);
        return {title: `지정가 주문 ${n}건이 체결되었습니다.`, body: parts.join(' · ')};
    }

    const {body} = splitMessage(latest.message);
    const names = unique(items.map((i) => splitMessage(i.message).title.match(/^(.+?)님이 /)?.[1]));
    if (names.length > 0) {
        const who = names.length > 1 ? `${names[0]}님 외 ${names.length - 1}명` : `${names[0]}님`;
        return {title: `${who}이 댓글 ${n}개를 남겼어요.`, body};
    }
    const answers = items.every((i) => i.message.startsWith('문의에 답변'));
    return {title: answers ? `문의에 답변 ${n}개가 등록됐어요.` : `문의에 새 글 ${n}개가 달렸어요.`, body};
}
