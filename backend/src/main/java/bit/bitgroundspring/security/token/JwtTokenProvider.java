package bit.bitgroundspring.security.token;

import bit.bitgroundspring.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expired_time}")
    private int jwtExpiredTime;

    @Value("${jwt.issuer}")
    private String expectedIssuer;

    @Value("${jwt.audience}")
    private String expectedAudience;

    private Key key;
    private JwtParser parser;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(expectedIssuer)
                .requireAudience(expectedAudience)
                .build();
    }

    /** 사용자 정보로 액세스 토큰 발급 (권한은 발급 시점의 DB 값) */
    public String createToken(User user) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .setSubject(user.getProviderId())
                .claim("provider", user.getProvider())
                .claim("auth", user.getRole().name())
                .claim("userId", user.getId())
                .setIssuedAt(new Date(now))
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .signWith(key, SignatureAlgorithm.HS512)
                .setExpiration(new Date(now + jwtExpiredTime))
                .compact();
    }

    /**
     * 서명·만료·발급자·대상을 검증하고 클레임을 반환한다. 검증과 파싱을 한 번에 한다.
     * 만료·위조 토큰은 흔한 요청이므로 스택트레이스 없이 DEBUG로만 남긴다.
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(parser.parseClaimsJws(token).getBody());
        } catch (ExpiredJwtException e) {
            log.debug("만료된 JWT 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT 토큰입니다: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public Authentication getAuthentication(Claims claims) {
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        String provider = (String) claims.get("provider");
        boolean admin = authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        AuthUser principal = new AuthUser(claims.get("userId", Integer.class), claims.getSubject(), provider, admin);

        // credentials는 보관하지 않음
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(provider);
        return authentication;
    }

}
