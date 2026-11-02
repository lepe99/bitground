package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.response.ReserveOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.CreateOrderRequest;
import bit.bitgroundspring.entity.Order;
import bit.bitgroundspring.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trade/reserve")
@RequiredArgsConstructor
public class ReservedOrderController {
    
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ReserveOrderResponse> placeReservedOrder(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateOrderRequest createOrderRequest
            ) {
        
        // 예약 주문 처리
        Order reserveOrder = orderService.createReserveOrder(authUser.id(), createOrderRequest);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ReserveOrderResponse.from(reserveOrder));
    }


}
