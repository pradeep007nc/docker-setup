package dev.pradeep.dockerbackend.resource.interceptor;

import dev.pradeep.dockerbackend.resource.annotation.RequiresPermission;
import dev.pradeep.dockerbackend.shared.model.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Intercepts handler method invocations to enforce fine-grained permission checks
 * declared via {@link RequiresPermission}.
 *
 * Roles (ROLE_X) are enforced at the SecurityFilterChain level or via @PreAuthorize.
 * Permissions (PERM_X) are enforced here, allowing per-service policy control.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Check method-level annotation first, fall back to class-level
        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }

        if (annotation == null) {
            return true; // no permission requirement on this endpoint
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return false;
        }

        // Extract permissions from authorities (stored as PERM_<name>)
        Set<String> userPermissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("PERM_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());

        String[] required = annotation.value();
        boolean hasAccess = annotation.requireAll()
                ? Arrays.stream(required).allMatch(userPermissions::contains)
                : Arrays.stream(required).anyMatch(userPermissions::contains);

        if (!hasAccess) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: missing required permission(s): " + Arrays.toString(required));
            return false;
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
