package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.response.TradeSummaryDto;
import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.dto.response.TradeDetailDto;
import bit.bitgroundspring.dto.response.OrderDto;
import bit.bitgroundspring.dto.request.CreateOrderRequest;
import bit.bitgroundspring.common.money.Money;
import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.common.tx.AfterCommit;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.entity.Message;
import bit.bitgroundspring.entity.MessageType;
import bit.bitgroundspring.dto.response.NotificationResponse;
import bit.bitgroundspring.entity.*;
import bit.bitgroundspring.repository.*;
import bit.bitgroundspring.service.UserSseEmitters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final SeasonRepository seasonRepository;
    private final TradingWindow tradingWindow;
    private final ReservedOrderBook orderBook;
    private final CoinRepository coinRepository;
    private final UserSseEmitters userSseEmitters;
    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;

    public List<OrderProjection> getOrdersBySeason(Integer seasonId, Integer userId) {
        return orderRepository.findBySeasonIdAndUserId(seasonId, userId);
    }

    public List<TradeDto> getRecentTrades(String symbol) {
        return orderRepository.findTop30ByCoinSymbolAndStatus(symbol, Status.COMPLETED);
    }

    public List<TradeSummaryDto> getTradeSummary(User user, Season season) {
        List<Order> orders = orderRepository.findByUserAndSeason(user, season);

        // 코인 심볼 기준 그룹
        Map<String, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCoin().getSymbol()));

        List<TradeSummaryDto> summaries = new ArrayList<>();

        for (Map.Entry<String, List<Order>> entry : grouped.entrySet()) {
            String coin = entry.getKey();
            List<Order> coinOrders = entry.getValue();

            // 매수/매도 분리
            List<Order> buys = coinOrders.stream()
                    .filter(o -> o.getOrderType().name().equals("BUY"))
                    .toList();
            List<Order> sells = coinOrders.stream()
                    .filter(o -> o.getOrderType().name().equals("SELL"))
                    .toList();

            // 합계는 BigDecimal로 정확히 구하고, 화면용 평균·수익률만 double로 바꾼다
            double buyTotal = sumValue(buys).doubleValue();
            double sellTotal = sumValue(sells).doubleValue();

            double buyQty = sumQuantity(buys).doubleValue();
            double sellQty = sumQuantity(sells).doubleValue();

            double avgBuy = buyQty == 0 ? 0 : buyTotal / buyQty;
            double avgSell = sellQty == 0 ? 0 : sellTotal / sellQty;
            double profit = sellTotal - buyTotal;
            String returnRate = buyTotal == 0 ? "0%" :
                    String.format("%+.2f%%", (profit / buyTotal) * 100);

            String buyDate = buys.stream()
                    .map(Order::getCreatedAt)
                    .min(LocalDateTime::compareTo)
                    .map(dt -> String.format("%02d-%02d", dt.getMonthValue(), dt.getDayOfMonth()))
                    .orElse("N/A");

            String koreanName = coinOrders.get(0).getCoin().getKoreanName();

            summaries.add(TradeSummaryDto.builder()
                    .coin(coin)
                    .koreanName(koreanName)
                    .buyDate(buyDate)
                    .buyAmount(buyTotal)
                    .sellAmount(sellTotal)
                    .avgBuy(avgBuy)
                    .avgSell(avgSell)
                    .profit(profit)
                    .returnRate(returnRate)
                    .build());
        }

        return summaries;
    }
    public List<TradeDetailDto> getTradeDetails(User user, Season season) {
        List<Order> orders = orderRepository.findByUserAndSeason(user, season);

        return orders.stream().map(order -> {
            String date = String.format("%02d-%02d",
                    order.getCreatedAt().getMonthValue(),
                    order.getCreatedAt().getDayOfMonth());

            String type = order.getOrderType().name().equals("BUY") ? "매수" : "매도";

            String coin = order.getCoin().getSymbol().replace("KRW-", "");
            String qty = order.getAmount().stripTrailingZeros().toPlainString() + " " + coin;
            String koreanName = order.getCoin().getKoreanName();

            BigDecimal price = order.getTradePrice();
            long total = order.getOrderType() == OrderType.BUY
                    ? Money.krwCeil(price, order.getAmount())
                    : Money.krwFloor(price, order.getAmount());

            return TradeDetailDto.builder()
                    .date(date)
                    .type(type)
                    .qty(qty)
                    .price(price)
                    .total(total)
                    .koreanName(koreanName)
                    .build();
        }).toList();
    }
    
    
    // 예약 주문 생성 메서드
    @Transactional
    @Loggable("예약 주문 접수")
    public Order createReserveOrder(Integer userId, CreateOrderRequest request) {
        validateOrderRequest(request);
        tradingWindow.ensureOpen(); // 시즌 종료 시각부터 정산이 끝날 때까지 거래 마감

        // 동시에 들어온 예약 주문이 같은 잔액을 중복 사용하지 않도록 사용자 행을 잠금
        User user = userRepository.findByIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Coin coin = coinRepository.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new BusinessException(ErrorCode.COIN_NOT_FOUND));
        
        Season season = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
        
        if (Boolean.TRUE.equals(coin.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COIN_DELISTED);
        }
        
        Order order = Order.builder()
                .user(user)
                .season(season)
                .coin(coin)
                .orderType(request.getOrderType())
                .amount(Money.quantity(Money.of(request.getAmount())))
                .reservePrice(Money.price(Money.of(request.getReservePrice())))
                .status(Status.PENDING)
                .build();
        
        // 주문 유효성 검사 (현재 시즌 미체결 예약 주문에 이미 묶인 현금·수량을 빼고 확인)
        if (request.getOrderType() == OrderType.BUY) {
            long cost = Money.krwCeil(order.getReservePrice(), order.getAmount());
            if (orderRepository.reservedBuyCash(user.getId()) + cost > user.getCash()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH);
            }
        } else if (request.getOrderType() == OrderType.SELL) {
            UserAsset userAsset = userAssetRepository.findByUserAndCoinWithLock(user, coin)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_ASSET));
            // 같은 코인 중복 매도 예약 방지
            BigDecimal reservedAmount = orderRepository.sumPendingSellAmount(user.getId(), coin.getId());
            if (userAsset.getAmount().subtract(reservedAmount).compareTo(order.getAmount()) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_ASSET);
            }
        }
        
        Order savedOrder = orderRepository.save(order);
        // 커밋된 주문만 체결 대기열에 올린다 (롤백되면 Redis에도 남지 않음)
        AfterCommit.run(() -> orderBook.add(savedOrder));
        
        log.info("Created reserve order: {} for user: {}", savedOrder.getId(), savedOrder.getUser().getId());
        return savedOrder;
    }
    
    private void validateOrderRequest(CreateOrderRequest request) {
        // 수량은 소수 10자리까지만 저장하므로 그보다 작은 수량은 0으로 본다
        if (request.getAmount() == null || Money.quantity(Money.of(Math.max(request.getAmount(), 0))).signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "주문 수량은 0보다 커야 합니다.");
        }
        if (request.getReservePrice() == null || request.getReservePrice() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "예약 가격은 0보다 커야 합니다.");
        }
        if (request.getSymbol() == null) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "종목은 필수입니다.");
        }
        if (request.getOrderType() == null) {
            throw new BusinessException(ErrorCode.INVALID_ORDER, "주문 종류는 필수입니다.");
        }
    }
    
    @Transactional
    @Loggable("예약 주문 취소")
    public void cancelOrder(Integer orderId, Integer userId) {
        // 체결과 동시에 처리되지 않도록 주문 행을 잠금 (체결이 먼저 끝났으면 아래에서 ORDER_NOT_PENDING)
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        
        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        if (order.getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_PENDING);
        }
        
        orderRepository.delete(order);
        AfterCommit.run(() -> orderBook.remove(order));
        
        log.info("Cancelled order: {} by user: {}", orderId, userId);
    }
    
    @Loggable("시즌 갱신")
    public void seasonUpdate(String seasonFlag) {
        // 대부분의 로직은 go serverless로 처리, 나머지 것들을 수행하자
        // 새 시즌이 생겼으면 거래 마감을 바로 풀도록 기억해 둔 종료 시각을 다시 읽는다
        tradingWindow.refresh();

        if (seasonFlag.equals("season")) {
            // 스케줄러가 이전 시즌 미체결 주문을 DB에서 지웠으므로 Redis 대기열을 DB 기준으로 다시 만든다
            // (기존: 지워진 뒤의 PENDING 주문을 조회해 아무것도 지우지 못하고 이전 시즌 주문이 Redis에 남음)
            int pending = orderBook.rebuild();
            log.info("Reserved order book rebuilt after season update: {} pending orders", pending);
        }

        // 사용자에게 시즌 종료 / 스플릿 업데이트 알림 전송
        Season currentSeason = seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON));
        String seasonName = currentSeason.getName();

        Map<String, Object> data = Map.of(
                "seasonName", seasonName,
                "seasonFlag", seasonFlag
        );
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .messageType(MessageType.INFO)
                .message(Message.SEASON_UPDATE)
                .data(data)
                .build();
        userSseEmitters.sendToAll(notificationResponse);
    }

    private static BigDecimal sumValue(List<Order> orders) {
        return orders.stream().map(o -> o.getTradePrice().multiply(o.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumQuantity(List<Order> orders) {
        return orders.stream().map(Order::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //미체결
    public List<OrderDto> getPendingOrders(Integer userId, Integer seasonId) {
        List<Order> orders = orderRepository.findPendingOrdersByUserAndSeason(userId, seasonId);
        return orders.stream().map(o ->
                new OrderDto(
                        o.getId(),
                        o.getCoin().getSymbol(),
                        o.getCoin().getKoreanName(),
                        o.getAmount(),
                        o.getTradePrice(),
                        o.getReservePrice(),
                        o.getCreatedAt(),
                        o.getUpdatedAt(),
                        o.getOrderType().name()
                )
        ).toList();
    }

    // 현재 시즌 ID 반환
    public Integer getCurrentSeasonId() {
        return seasonRepository.findByStatus(Status.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_ACTIVE_SEASON))
                .getId();
    }


}
