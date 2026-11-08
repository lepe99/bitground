package bit.bitgroundspring.service;

import bit.bitgroundspring.entity.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 알림 문구. 프론트의 알림 묶기(groupNotifications.js)가 첫 단어와 '수량 : 0.1 BTC' 형식을 본다 */
class NotificationMessagesTest {

    @Test
    void 예약_체결() {
        assertThat(NotificationMessages.orderExecuted(OrderType.BUY, "KRW-BTC", new BigDecimal("0.01"), new BigDecimal("101234000")))
                .isEqualTo("예약 매수 주문이 체결되었습니다.\n수량 : 0.0100000 BTC\n체결 : 개당 101,234,000원");
        assertThat(NotificationMessages.orderExecuted(OrderType.SELL, "KRW-DOGE", new BigDecimal("1234.5"), new BigDecimal("512.30")))
                .isEqualTo("예약 매도 주문이 체결되었습니다.\n수량 : 1234.5000000 DOGE\n체결 : 개당 512.3원");
    }

    @Test
    void 예약_취소() {
        assertThat(NotificationMessages.orderCancelled(OrderType.BUY, "비트코인", new BigDecimal("0.0100"), new BigDecimal("90000000")))
                .isEqualTo("잔액이 부족해 예약 매수 주문이 취소됐어요.\n비트코인 0.01개 · 예약가 90,000,000원");
        assertThat(NotificationMessages.orderCancelled(OrderType.SELL, "시바이누", new BigDecimal("1000"), new BigDecimal("0.0123")))
                .isEqualTo("보유 수량이 부족해 예약 매도 주문이 취소됐어요.\n시바이누 1000개 · 예약가 0.0123원");
    }

    @Test
    void 시즌과_공지() {
        assertThat(NotificationMessages.seasonUpdate("season", "26년 2시즌")).startsWith("🎉 26년 2시즌 스플릿 1 🎉\n");
        assertThat(NotificationMessages.seasonUpdate("split", "26년 2시즌")).contains("10,000,000원의 추가 자금");
        assertThat(NotificationMessages.seasonUpdate("unknown", "x")).isNull();
        assertThat(NotificationMessages.notice("점검 안내")).endsWith("\n[점검 안내]");
    }
}
