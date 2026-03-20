package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_id", "service_id"}))
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private AppService service;

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public AppService getService() { return service; }
    public void setService(AppService service) { this.service = service; }
}
