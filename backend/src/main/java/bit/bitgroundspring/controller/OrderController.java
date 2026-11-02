package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.OrderDto;
import bit.bitgroundspring.dto.projection.OrderProjection;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import bit.bitgroundspring.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import bit.bitgroundspring.security.oauth2.AuthService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SeasonRepository seasonRepository;

    @GetMapping("/{seasonId}")
    public List<OrderProjection> getOrders(
            @PathVariable Integer seasonId,
            @CookieValue("jwt_token") String jwtToken) {

        Integer userId = authService.getUserIdFromToken(jwtToken); // ✅ 로그인한 사용자만
        return orderService.getOrdersBySeason(seasonId, userId);
    }
    
    @GetMapping("/reserve")
    public ResponseEntity<List<OrderDto>> getReserveOrders(
            @CookieValue("jwt_token") String jwtToken) {

        // 1. 로그인 유저 ID 추출
        Integer userId = authService.getUserIdFromToken(jwtToken);

        // 2. 현재 시즌 ID 조회
        Integer currentSeasonId = orderService.getCurrentSeasonId(); // 또는 SeasonService 사용

        // 3. 예약 주문만 조회
        List<OrderDto> orders = orderService.getPendingOrders(userId, currentSeasonId);

        return ResponseEntity.ok(orders);
    }

    //삭제
    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> cancelReserveOrder(
            @PathVariable Integer orderId,
            @CookieValue("jwt_token") String jwtToken) {

        Integer userId = authService.getUserIdFromToken(jwtToken);
        orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok().build();
    }


}
