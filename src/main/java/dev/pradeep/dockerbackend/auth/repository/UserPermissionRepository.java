package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.AppUser;
import dev.pradeep.dockerbackend.auth.model.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUserAndService(AppUser user, AppService service);
}
