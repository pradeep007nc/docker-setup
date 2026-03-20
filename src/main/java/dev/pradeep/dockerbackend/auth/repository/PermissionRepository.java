package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByPermissionNameAndService(String permissionName, AppService service);
    boolean existsByPermissionNameAndService(String permissionName, AppService service);
}
