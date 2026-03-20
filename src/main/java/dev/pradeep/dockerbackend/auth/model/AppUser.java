package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"username", "service_id"}))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    private String email;

    @Column(nullable = false)
    private String password;

    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private AppService service;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public AppService getService() { return service; }
    public void setService(AppService service) { this.service = service; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}