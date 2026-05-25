package com.barista.config;

import com.barista.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * LOCAL mode (default): all requests permitted, no login required.
 * OAUTH mode:  REST /api/** requires authentication (returns 401 JSON when missing).
 *              Static assets, WebSocket, and OAuth endpoints are always permitted.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${barista.auth.mode:local}")
    private String authMode;

    @Autowired
    private UserService userService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Always disable CSRF — Arima is a local-use SPA
        http.csrf(AbstractHttpConfigurer::disable);

        if (userService.isLocalMode()) {
            // ── Local mode: no auth at all ───────────────────────────
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        } else {
            // ── OAuth mode: protect API, allow everything else ───────
            http.authorizeHttpRequests(auth -> auth
                            // Auth infrastructure + static assets always open
                            .requestMatchers(
                                    "/", "/index.html",
                                    "/css/**", "/js/**", "/img/**", "/favicon*",
                                    "/ws/**", "/sockjs-node/**",
                                    "/oauth2/**", "/login/oauth2/**", "/error"
                            ).permitAll()
                            // /api/user/me returns guest info when not authenticated — always accessible
                            .requestMatchers("/api/user/**").permitAll()
                            // All other API calls require authentication
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll()
                    )
                    .oauth2Login(oauth -> oauth
                            // After successful OAuth login redirect back to the SPA
                            .defaultSuccessUrl("/?auth=ok", true)
                    )
                    // For unauthenticated /api/** calls: return 401 JSON instead of redirect
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((req, resp, e) -> {
                                if (req.getRequestURI().startsWith("/api/")) {
                                    resp.setStatus(401);
                                    resp.setContentType("application/json;charset=UTF-8");
                                    resp.getWriter().write("{\"error\":\"Authentication required\",\"authRequired\":true}");
                                } else {
                                    resp.sendRedirect("/");
                                }
                            })
                    );
        }

        return http.build();
    }
}
