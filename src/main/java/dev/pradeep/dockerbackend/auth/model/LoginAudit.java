package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit log for login events only — both successful and failed attempts.
 * Never written to on regular API requests to avoid noise.
 *
 * Stores strings (not FK relationships) so the record persists even if the user
 * or service is later deleted.
 */
@Entity
@Table(name = "login_audit", indexes = {
        @Index(name = "idx_audit_username_service", columnList = "username, serviceId"),
        @Index(name = "idx_audit_login_at", columnList = "loginAt")
})
public class LoginAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String serviceId;

    /** PASSWORD or OTP */
    @Column(nullable = false)
    private String authMethod;

    private boolean success;

    /** Populated only on failure — reason kept generic to avoid leaking details to logs */
    private String failureReason;

    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime loginAt = LocalDateTime.now();

    /** Populated only on success — lets admins see when the issued token expires */
    private LocalDateTime tokenExpiresAt;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getAuthMethod() { return authMethod; }
    public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getLoginAt() { return loginAt; }
    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
}
