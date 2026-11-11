package bit.bitgroundspring.notification;

import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.order.OrderType;
import bit.bitgroundspring.season.InitialCashPolicy;

import java.math.BigDecimal;

/**
 * 서버가 저장하는 알림 문구 (첫 줄은 제목, 나머지는 본문).
 * 실시간 알림도 저장한 문구를 그대로 실어 보내고 프론트는 받은 문구를 쓴다
 * (예전: 서버 저장 문구와 프론트 토스트 문구를 각자 조립해 문구를 바꾸면 목록과 토스트가 어긋났다).
 * 댓글·문의 문구는 CommentService·InquiryNotifier, 상장 폐지 정리는 Go 스케줄러(delisting.go)가 만든다
 */
public final class NotificationMessages {

    private NotificationMessages() {
    }

    /**
     * 지정가(예약) 주문 체결. 본문은 2줄: 수량·체결가 / 수수료·실제로 쓴(받은) 금액.
     * 프론트의 알림 묶기가 '지정가'(예전 문구는 '예약')로 시작하는지와 둘째 줄의 '0.1 BTC · 개당' 형식을 본다 (groupNotifications.js)
     * fee가 null이면(수수료 도입 전 이벤트) 수수료 줄을 넣지 않는다
     */
    public static String orderExecuted(OrderType orderType, String symbol, BigDecimal amount, BigDecimal tradePrice, Long fee) {
        String coin = symbol.contains("-") ? symbol.split("-")[1] : symbol;
        String text = String.format("지정가 %s 주문이 체결되었습니다.\n%s %s · 개당 %s원",
                side(orderType), amount.stripTrailingZeros().toPlainString(), coin, price(tradePrice));
        if (fee == null) {
            return text;
        }
        // 체결과 같은 계산: 매수는 대금(올림) + 수수료를 쓰고, 매도는 대금(내림) − 수수료를 받는다
        boolean buy = orderType == OrderType.BUY;
        long settled = buy ? Money.krwCeil(tradePrice, amount) + fee : Money.krwFloor(tradePrice, amount) - fee;
        return text + String.format("\n수수료 %,d원 · %,d원 %s", fee, settled, buy ? "사용" : "받음");
    }

    /** 체결 시점에 잔액(수수료 포함)·보유 수량이 모자라 지정가 주문을 취소함 */
    public static String orderCancelled(OrderType orderType, String coinName, BigDecimal amount, BigDecimal reservePrice) {
        return String.format("%s 지정가 %s 주문이 취소됐어요.\n%s %s개 · 주문 가격 %s원",
                orderType == OrderType.BUY ? "잔액이 부족해" : "보유 수량이 부족해", side(orderType),
                coinName, amount.stripTrailingZeros().toPlainString(), price(reservePrice));
    }

    public static String notice(String title) {
        return String.format("🔔 새로운 공지사항이 등록되었습니다.\n공지사항 탭에서 확인해보세요.\n[%s]", title);
    }

    /** 스케줄러가 알린 시즌 갱신 (season: 새 시즌, split: 스플릿 2 시작). 모르는 값이면 null */
    public static String seasonUpdate(String seasonFlag, String seasonName) {
        return switch (seasonFlag) {
            case "season" -> String.format("🎉 %s 스플릿 1 🎉\n새로운 시즌이 시작되었습니다!\n이전 랭킹과 수익률을 확인해보세요.", seasonName);
            case "split" -> String.format("🚀 %s 스플릿 2 🚀\n새로운 스플릿이 시작되었습니다!\n%,d원의 추가 자금이 지급됩니다.",
                    seasonName, InitialCashPolicy.SPLIT_CASH);
            default -> null;
        };
    }

    private static String side(OrderType orderType) {
        return orderType == OrderType.BUY ? "매수" : "매도";
    }

    /** 정수면 소수점 없이, 소수면 불필요한 0을 빼고 천 단위 구분 (1,234 / 0.0123 / 512.3) */
    static String price(BigDecimal price) {
        if (price == null) {
            return "0";
        }
        BigDecimal stripped = price.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return String.format("%,d", stripped.longValueExact());
        }
        return String.format("%,." + stripped.scale() + "f", stripped);
    }
}
