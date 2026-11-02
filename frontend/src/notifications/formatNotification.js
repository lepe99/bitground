// 서버가 SSE로 보낸 알림(NotificationResponse)을 토스트 문구로 바꾼다
export const formatNotification = ({message, data}) => {
    switch (message) {
        case 'ORDER_EXECUTION': {
            const {orderType, symbol, amount, tradePrice} = data;
            const cutSymbol = symbol.split('-')[1];
            return `예약 ${orderType === 'BUY' ? '매수' : '매도'} 주문이 체결되었습니다.\n`
                + `수량 : ${parseFloat(amount).toFixed(7)} ${cutSymbol}\n`
                + `체결 : 개당 ${Number(tradePrice).toLocaleString()}원`;
        }
        case 'SEASON_UPDATE': {
            const {seasonName, seasonFlag} = data;
            if (seasonFlag === 'season') {
                return `🎉 ${seasonName} 스플릿 1 🎉\n새로운 시즌이 시작되었습니다!\n이전 랭킹과 수익률을 확인해보세요.`;
            }
            if (seasonFlag === 'split') {
                return `🚀 ${seasonName} 스플릿 2 🚀\n새로운 스플릿이 시작되었습니다!\n10,000,000원의 추가 자금이 지급됩니다.`;
            }
            return '';
        }
        case 'NOTICE':
            return `🔔 새로운 공지사항이 등록되었습니다.\n공지사항 탭에서 확인해보세요.\n[${data.title}]\n`;
        default:
            return '';
    }
};
