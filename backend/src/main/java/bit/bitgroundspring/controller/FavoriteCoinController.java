package bit.bitgroundspring.controller;

import bit.bitgroundspring.security.token.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import bit.bitgroundspring.service.FavoriteCoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteCoinController {

    private final FavoriteCoinService favService;

    @PostMapping
    public ResponseEntity<Void> addFav(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam String symbol
    ) {
        Integer userId = authUser.id();
        favService.addFavorite(userId, symbol);
        URI location = URI.create("/api/favorites/" + symbol);
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeFav(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable String symbol
    ) {
        Integer userId = authUser.id();
        favService.removeFavorite(userId, symbol);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<String>> listFav(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        Integer userId = authUser.id();
        List<String> symbols = favService.listFavorites(userId);
        return ResponseEntity.ok(symbols);
    }
}