package dev.pradeep.dockerbackend.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TokenResponse {
    private String accessToken;
    private String tokenType;
    private List<String> roles;
    private List<String> permissions;
    private String serviceId;
    private String authMethod;
    private LocalDateTime expiresAt;

    public TokenResponse(String accessToken, String tokenType,
                         List<String> roles, List<String> permissions,
                         String serviceId, String authMethod, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.roles = roles;
        this.permissions = permissions;
        this.serviceId = serviceId;
        this.authMethod = authMethod;
        this.expiresAt = expiresAt;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public List<String> getRoles() { return roles; }
    public List<String> getPermissions() { return permissions; }
    public String getServiceId() { return serviceId; }
    public String getAuthMethod() { return authMethod; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
