package bit.bitgroundspring.order;

import org.springframework.http.HttpStatus;
import bit.bitgroundspring.order.dto.OrderRequestDto;
import bit.bitgroundspring.order.dto.OrderResponseDto;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {
    private final TradeService tradeService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> placeOrder(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody OrderRequestDto req
    ) {
        Integer userId = authUser.id();
        OrderResponseDto resp = tradeService.placeOrder(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
