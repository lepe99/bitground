package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.dto.request.CreateOrderRequest;
import bit.bitgroundspring.dto.response.OrderDto;
import bit.bitgroundspring.dto.response.ReserveOrderResponse;
import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.security.token.AuthUser;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.SeasonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 내역과 예약 주문
 * 예약 주문은 접수·조회·취소를 /orders/reserve 아래에 둔다 (시장가 즉시 주문은 POST /trade)
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SeasonService seasonService;

    // 시즌별 내 주문 내역 (체결·미체결 전체)
    @GetMapping
    public List<OrderProjection> getOrders(@AuthenticationPrincipal AuthUser authUser,
                                           @RequestParam Integer seasonId) {
        return orderService.getOrdersBySeason(seasonId, authUser.id());
    }

    // 예약 주문 접수
    @PostMapping("/reserve")
    public ResponseEntity<ReserveOrderResponse> createReserveOrder(@AuthenticationPrincipal AuthUser authUser,
                                                                   @Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createReserveOrder(authUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReserveOrderResponse.from(order));
    }

    // 현재 시즌의 미체결 예약 주문
    @GetMapping("/reserve")
    public List<OrderDto> getReserveOrders(@AuthenticationPrincipal AuthUser authUser) {
        return orderService.getPendingOrders(authUser.id(), seasonService.getCurrentSeasonId());
    }

    // 예약 주문 취소 (본인 주문, 미체결일 때만)
    @DeleteMapping("/reserve/{orderId}")
    public ResponseEntity<Void> cancelReserveOrder(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable Integer orderId) {
        orderService.cancelOrder(orderId, authUser.id());
        return ResponseEntity.noContent().build();
    }
}
