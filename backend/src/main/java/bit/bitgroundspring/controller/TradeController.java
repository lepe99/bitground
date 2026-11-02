package bit.bitgroundspring.controller;

import org.springframework.http.HttpStatus;
import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.dto.request.OrderRequestDto;
import bit.bitgroundspring.dto.response.TradeDto;
import bit.bitgroundspring.dto.response.OrderResponseDto;
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

    @GetMapping("/history")
    public ResponseEntity<List<TradeDto>> getTradeHistory(
            @RequestParam("symbol") String symbol
    ) {
        List<TradeDto> history = orderService.getRecentTrades(symbol);
        return ResponseEntity.ok(history);
    }

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
