package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores a pending OTP challenge for a user+service pair.
 * Expires in 5 minutes and is single-use (used flag flipped on verification).
 */
@Entity
@Table(name = "otp_requests")
public class OtpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private AppService service;

    /** Plain OTP code (demo only — in production use TOTP/HOTP and never store the code) */
    @Column(nullable = false)
    private String otpCode;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AppService getService() { return service; }
    public void setService(AppService service) { this.service = service; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
