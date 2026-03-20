package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"permission_name", "service_id"}))
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_name", nullable = false)
    private String permissionName;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private AppService service;

    public Long getId() { return id; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AppService getService() { return service; }
    public void setService(AppService service) { this.service = service; }
}
