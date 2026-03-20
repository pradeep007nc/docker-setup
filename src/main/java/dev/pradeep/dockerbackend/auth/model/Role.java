package dev.pradeep.dockerbackend.auth.model;

import jakarta.persistence.*;

@Entity
@Table(name = "roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_name", "service_id"}))
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private AppService service;

    public Long getId() { return id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AppService getService() { return service; }
    public void setService(AppService service) { this.service = service; }
}