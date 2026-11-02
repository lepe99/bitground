package bit.bitgroundspring.controller;

import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.OrderDto;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{seasonId}")
    public List<OrderProjection> getOrders(
            @PathVariable Integer seasonId,
            @AuthenticationPrincipal AuthUser authUser) {

        Integer userId = authUser.id();
        return orderService.getOrdersBySeason(seasonId, userId);
    }
    
    @GetMapping("/reserve")
    public ResponseEntity<List<OrderDto>> getReserveOrders(
            @AuthenticationPrincipal AuthUser authUser) {

        // 1. 로그인 유저 ID 추출
        Integer userId = authUser.id();

        // 2. 현재 시즌 ID 조회
        Integer currentSeasonId = orderService.getCurrentSeasonId(); // 또는 SeasonService 사용

        // 3. 예약 주문만 조회
        List<OrderDto> orders = orderService.getPendingOrders(userId, currentSeasonId);

        return ResponseEntity.ok(orders);
    }

    //삭제
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelReserveOrder(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal AuthUser authUser) {

        Integer userId = authUser.id();
        orderService.cancelOrder(orderId, userId);
        return ResponseEntity.noContent().build();
    }


}
