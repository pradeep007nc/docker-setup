package dev.pradeep.dockerbackend.auth.filter;

import dev.pradeep.dockerbackend.auth.service.TokenService;
import dev.pradeep.dockerbackend.auth.service.TokenService.TokenStatus;
import dev.pradeep.dockerbackend.shared.model.UserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts every request, extracts the Bearer JWT from the Authorization header,
 * and populates the SecurityContext with the authenticated user.
 *
 * Token expiry is handled explicitly:
 *   - Expired token  → 401 with code TOKEN_EXPIRED  (client must re-authenticate)
 *   - Tampered token → 401 with code INVALID_TOKEN
 *   - No token       → passes through unauthenticated (public endpoints handle it)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        TokenStatus status = tokenService.getStatus(token);

        if (status == TokenStatus.EXPIRED) {
            sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_EXPIRED", "Your token has expired. Please log in again.");
            return;
        }

        if (status == TokenStatus.INVALID) {
            sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", "Token is malformed or has been tampered with.");
            return;
        }

        // Token is VALID — parse claims and set SecurityContext
        Claims claims = tokenService.parseToken(token);

        String username  = claims.getSubject();
        Long   userId    = claims.get("userId", Long.class);
        String serviceId = claims.get("serviceId", String.class);
        String authMethod = claims.get("authMethod", String.class);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);

        UserPrincipal principal = new UserPrincipal(userId, username, serviceId, roles, permissions);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (roles != null)       roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        if (permissions != null) permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void sendAuthError(HttpServletResponse response, int status,
                                String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\": \"" + message + "\", \"code\": \"" + code + "\"}"
        );
    }
}
