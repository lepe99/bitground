package bit.bitgroundspring.config;

import bit.bitgroundspring.security.oauth2.CustomOidcUserService;
import bit.bitgroundspring.security.oauth2.OidcAuthenticationFailureHandler;
import bit.bitgroundspring.security.oauth2.OidcAuthenticationSuccessHandler;
import bit.bitgroundspring.security.token.JwtAuthenticationFilter;
import bit.bitgroundspring.security.token.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomOidcUserService customOidcUserService;
    private final OidcAuthenticationSuccessHandler oidcAuthenticationSuccessHandler;
    private final OidcAuthenticationFailureHandler oidcAuthenticationFailureHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${react.host}")
    private String reactHost;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        }))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/auth/refresh", "/oauth2/**", "/error", "/favicon.ico", "/test", "/ws/**", "/actuator/*", // 인증, 테스트
                                "/coins/**", "/coins", "/news", "/market-index/**", "/ai-insights/**", "/public/**", // 공개 API
                                "/auth/logout", "/seasons/update").permitAll()
                        .requestMatchers("/inquiries/*/answer", "/api/docs/**").hasRole("ADMIN")
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
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(reactHost, "http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}