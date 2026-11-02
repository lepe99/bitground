package bit.bitgroundspring.controller;

import bit.bitgroundspring.dto.UserDto;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.security.oauth2.AuthService;
import bit.bitgroundspring.service.FavoriteCoinService;
import bit.bitgroundspring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteCoinController {

    private final AuthService authService;
    private final FavoriteCoinService favService;

    @PostMapping
    public ResponseEntity<Void> addFav(
            @CookieValue("jwt_token") String jwtToken,
            @RequestParam String symbol
    ) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        favService.addFavorite(userId, symbol);
        URI location = URI.create("/api/favorites/" + symbol);
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeFav(
            @CookieValue("jwt_token") String jwtToken,
            @PathVariable String symbol
    ) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        favService.removeFavorite(userId, symbol);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<String>> listFav(
            @CookieValue("jwt_token") String jwtToken
    ) {
        Integer userId = authService.getUserIdFromToken(jwtToken);
        List<String> symbols = favService.listFavorites(userId);
        return ResponseEntity.ok(symbols);
    }
}