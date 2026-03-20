package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.LoginAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {

    /** Most recent successful login for a user in a service */
    Optional<LoginAudit> findTopByUsernameAndServiceIdAndSuccessTrueOrderByLoginAtDesc(
            String username, String serviceId);

    /** Paginated full audit log for a user+service — for admin queries */
    Page<LoginAudit> findByUsernameAndServiceIdOrderByLoginAtDesc(
            String username, String serviceId, Pageable pageable);

    /** All events for a service (admin overview) */
    Page<LoginAudit> findByServiceIdOrderByLoginAtDesc(String serviceId, Pageable pageable);
}
