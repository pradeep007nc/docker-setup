package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.AppUser;
import dev.pradeep.dockerbackend.auth.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserAndService(AppUser user, AppService service);
}
