package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.OrderType;

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

    /** 예약 주문 체결. 프론트의 알림 묶기가 '예약'으로 시작하는지와 '수량 : 0.1 BTC' 형식을 본다 (groupNotifications.js) */
    public static String orderExecuted(OrderType orderType, String symbol, BigDecimal amount, BigDecimal tradePrice) {
        String coin = symbol.contains("-") ? symbol.split("-")[1] : symbol;
        return String.format("예약 %s 주문이 체결되었습니다.\n수량 : %.7f %s\n체결 : 개당 %s원",
                side(orderType), amount, coin, price(tradePrice));
    }

    /** 체결 시점에 잔액·보유 수량이 모자라 예약 주문을 취소함 */
    public static String orderCancelled(OrderType orderType, String coinName, BigDecimal amount, BigDecimal reservePrice) {
        return String.format("%s 예약 %s 주문이 취소됐어요.\n%s %s개 · 예약가 %s원",
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
