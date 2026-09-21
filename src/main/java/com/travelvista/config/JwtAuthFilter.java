package com.travelvista.config;

import com.travelvista.model.User;
import com.travelvista.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);

                    User user = userRepository.findByEmail(email).orElse(null);
                    boolean adminRole = user != null && user.getRole() != null
                            && ("admin".equals(user.getRole().getName())
                            || "super_admin".equals(user.getRole().getName())
                            || "content_manager".equals(user.getRole().getName())
                            || "editor".equals(user.getRole().getName()));
                    if (user != null && Boolean.TRUE.equals(user.getIsActive())
                            && (adminRole || Boolean.TRUE.equals(user.getEmailVerified()))) {
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
                        var auth = new UsernamePasswordAuthenticationToken(
                                user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (Exception ignored) {
                // Invalid token — continue without auth
            }
        }

        filterChain.doFilter(request, response);
    }
}
