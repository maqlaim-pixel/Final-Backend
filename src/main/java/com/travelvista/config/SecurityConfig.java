//package com.travelvista.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.HttpMethod;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.CorsConfigurationSource;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
//    private final JwtAuthFilter jwtAuthFilter;
//
//    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
//        this.jwtAuthFilter = jwtAuthFilter;
//    }
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
//            .csrf(csrf -> csrf.disable())
//            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, error) -> {
//                res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"error\":\"Authentication required\"}");
//            }))
//            .authorizeHttpRequests(auth -> auth
//                // ── Public endpoints (no auth needed) ──
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/admin/login")).permitAll()
//
//                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register/verify", "/api/auth/register/resend",
//                    "/api/auth/login", "/api/auth/login/verify", "/api/auth/login/otp/send", "/api/auth/login/otp/verify",
//                    "/api/auth/login/otp/resend", "/api/auth/forgot-password", "/api/auth/forgot-password/verify", "/api/auth/reset-password").permitAll()
//                .requestMatchers("/api/admin/**").hasAnyRole("admin", "super_admin", "content_manager", "editor")
//                .requestMatchers(HttpMethod.GET, "/api/invoices/my").hasRole("customer")
//                .requestMatchers(HttpMethod.GET, "/api/invoices/*", "/api/invoices/*/pdf").hasAnyRole("customer", "admin", "super_admin", "content_manager", "editor")
//                .requestMatchers("/api/invoices/**").hasAnyRole("admin", "super_admin", "content_manager", "editor")
//                .requestMatchers("/api/auth/me").hasRole("customer")
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/packages/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/destinations/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/hotels/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/activities/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/blogs/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/testimonials")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/faqs")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/settings")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/api/dashboard/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/images/**")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/leads/public/submit")).permitAll()
//                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/health")).permitAll()
//                // ── Everything else under /api requires authentication ──
//                .anyRequest().authenticated()
//            )
//            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
//
//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowedOriginPatterns(List.of("*"));
//        config.setAllowedOrigins(List.of());
//        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
//        config.setAllowedHeaders(List.of("*"));
//        config.setAllowCredentials(true);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/api/**", config); // AntPathMatcher handles this fine in CorsConfigurationSource
//        return source;
//    }
//}


package com.travelvista.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())

                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint((req, res, error) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"Authentication required\"}"
                            );
                        })
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow CORS preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Admin login
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.POST,
                                        "/api/admin/login"
                                )
                        ).permitAll()

                        // Authentication
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/register/verify",
                                "/api/auth/register/resend",
                                "/api/auth/login",
                                "/api/auth/login/verify",
                                "/api/auth/login/otp/send",
                                "/api/auth/login/otp/verify",
                                "/api/auth/login/otp/resend",
                                "/api/auth/forgot-password",
                                "/api/auth/forgot-password/verify",
                                "/api/auth/reset-password"
                        ).permitAll()

                        // Admin
                        .requestMatchers("/api/admin/**")
                        .hasAnyRole(
                                "admin",
                                "super_admin",
                                "content_manager",
                                "editor"
                        )

                        // Invoices
                        .requestMatchers(HttpMethod.GET, "/api/invoices/my")
                        .hasRole("customer")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/invoices/*",
                                "/api/invoices/*/pdf"
                        )
                        .hasAnyRole(
                                "customer",
                                "admin",
                                "super_admin",
                                "content_manager",
                                "editor"
                        )

                        .requestMatchers("/api/invoices/**")
                        .hasAnyRole(
                                "admin",
                                "super_admin",
                                "content_manager",
                                "editor"
                        )

                        // Customer
                        .requestMatchers("/api/auth/me")
                        .hasRole("customer")

                        // Public GET endpoints
                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/packages/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/destinations/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/hotels/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/activities/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/blogs/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/testimonials"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/faqs"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/settings"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/api/dashboard/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        "/api/images/**"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.POST,
                                        "/api/leads/public/submit"
                                )
                        ).permitAll()

                        .requestMatchers(
                                AntPathRequestMatcher.antMatcher(
                                        HttpMethod.GET,
                                        "/health"
                                )
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        // Local frontend + deployed Vercel frontend
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "https://final-frontend-jet-zeta.vercel.app",
                "https://final-frontend-git-main-dhavalmaqlaim-5177.vercel.app"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Disposition"
        ));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // IMPORTANT: apply CORS to every endpoint
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}