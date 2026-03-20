package dev.pradeep.dockerbackend.shared.model;

import java.util.List;

/**
 * Holds the authenticated user's parsed claims from the JWT.
 * Set as the principal in SecurityContextHolder after JWT validation.
 */
public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final String serviceId;
    private final List<String> roles;
    private final List<String> permissions;

    public UserPrincipal(Long userId, String username, String serviceId,
                         List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.serviceId = serviceId;
        this.roles = roles != null ? roles : List.of();
        this.permissions = permissions != null ? permissions : List.of();
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getServiceId() { return serviceId; }
    public List<String> getRoles() { return roles; }
    public List<String> getPermissions() { return permissions; }

    @Override
    public String toString() {
        return "UserPrincipal{username='" + username + "', serviceId='" + serviceId + "', roles=" + roles + "}";
    }
}
