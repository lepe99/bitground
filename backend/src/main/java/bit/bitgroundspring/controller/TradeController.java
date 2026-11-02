package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.OrderRequestDto;
import bit.bitgroundspring.dto.TradeDto;
import bit.bitgroundspring.dto.response.OrderResponseDto;
import bit.bitgroundspring.security.oauth2.AuthService;
import bit.bitgroundspring.service.OrderService;
import bit.bitgroundspring.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeController {
    private final OrderService orderService;
    private final TradeService tradeService;
    private final AuthService authService;

    @GetMapping("/history")
    public ResponseEntity<List<TradeDto>> getTradeHistory(
            @RequestParam("symbol") String symbol
    ) {
        List<TradeDto> history = orderService.getRecentTrades(symbol);
        return ResponseEntity.ok(history);
    }

    @PostMapping
    public ResponseEntity<OrderResponseDto> placeOrder(
            @CookieValue (value = "jwt_token", required = false) String jwtToken,
            @RequestBody OrderRequestDto req
    ) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        OrderResponseDto resp = tradeService.placeOrder(userId, req);
        return ResponseEntity.ok(resp);
    }
}
