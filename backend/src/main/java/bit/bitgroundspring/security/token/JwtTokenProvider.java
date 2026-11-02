package bit.bitgroundspring.security.token;

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
import java.util.stream.Collectors;

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
    
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }
    
    public String createToken(Authentication authentication, int userId, String provider) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        
        long now = (new Date()).getTime();
        Date validity = new Date(now + jwtExpiredTime);
        
        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("provider", provider)
                .claim("auth", authorities)
                .claim("userId", userId)
                .setIssuedAt(new Date(now))
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .signWith(key, SignatureAlgorithm.HS512)
                .setExpiration(validity)
                .compact();
    }
    
    public String createTokenByRefresh(RefreshToken refreshToken) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + jwtExpiredTime);
        
        return Jwts.builder()
                .setSubject(refreshToken.getProviderId())
                .claim("provider", refreshToken.getProvider())
                .claim("auth", refreshToken.getRole().name())
                .claim("userId", refreshToken.getUserId())
                .setIssuedAt(new Date(now))
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .signWith(key, SignatureAlgorithm.HS512)
                .setExpiration(validity)
                .compact();
    }
    
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(expectedIssuer)
                    .requireAudience(expectedAudience)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.", e);
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.", e);
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.", e);
        } catch (Exception e) {
            log.info("JWT 토큰 검증 중 예상치 못한 오류 발생", e);
        }
        return false; // 검증 실패
    }
    
    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        
        String username = claims.getSubject();

        // 권한 정보 추출
        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
        
        // 인증된 사용자 정보를 담은 객체 생성
        // 두 번째 파라미터(credentials)는 보안상 이유로 null로 설정
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        
        // JWT 클레임 정보를 details에 저장
        String provider = (String) claims.get("provider");
        // 필요한 다른 클레임도 추가
        authentication.setDetails(provider);
        
        // 인증 객체 반환
        return authentication;
    }
    
}
