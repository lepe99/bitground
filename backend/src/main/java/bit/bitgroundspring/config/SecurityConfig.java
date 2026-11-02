package bit.bitgroundspring.config;

import bit.bitgroundspring.common.error.ProblemDetailSecurityHandler;
import bit.bitgroundspring.security.oauth2.CustomOidcUserService;
import bit.bitgroundspring.security.oauth2.OidcAuthenticationFailureHandler;
import bit.bitgroundspring.security.oauth2.OidcAuthenticationSuccessHandler;
import bit.bitgroundspring.security.token.JwtAuthenticationFilter;
import bit.bitgroundspring.security.token.AccessTokenBlocklist;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomOidcUserService customOidcUserService;
    private final OidcAuthenticationSuccessHandler oidcAuthenticationSuccessHandler;
    private final OidcAuthenticationFailureHandler oidcAuthenticationFailureHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final ProblemDetailSecurityHandler problemDetailSecurityHandler;

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/auth/refresh", "/oauth2/**", "/error", "/favicon.ico", "/ws/**", // 인증, WebSocket
                                "/coins/**", "/coins", "/news", "/market-index/**", "/ai-insights/**", "/public/**", // 공개 API
                                "/auth/logout", "/seasons/update").permitAll()
                        // 관리 포트(8091)에서만 열리는 엔드포인트. 외부 노출은 네트워크(보안그룹)로 막는다
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/notices").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/notices/*").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2Login -> oauth2Login
                        .userInfoEndpoint(userInfoEndpoint ->
                                userInfoEndpoint.oidcUserService(customOidcUserService)
                        )
                        .successHandler(oidcAuthenticationSuccessHandler)
                        .failureHandler(oidcAuthenticationFailureHandler)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, accessTokenBlocklist),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 허용 주소는 설정(cors.allowed-origins)으로만 정한다 (기존: localhost:5173을 운영에서도 항상 허용)
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}