package com.travelvista.config;

import com.travelvista.model.User;
import com.travelvista.repository.UserRepository;
import com.travelvista.service.UserService;
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
                    // Role names are normalized (case/whitespace) but the accepted role set is unchanged.
                    String normalizedRole = UserService.normalizeRole(UserService.roleName(user));
                    boolean hasRole = user != null && !normalizedRole.isBlank() && !"none".equals(normalizedRole);
                    boolean staffRole = UserService.ADMIN_ROLE_NAMES.contains(normalizedRole);
                    if (user != null && hasRole && Boolean.TRUE.equals(user.getIsActive())
                            && (staffRole || Boolean.TRUE.equals(user.getEmailVerified()))) {
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
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
