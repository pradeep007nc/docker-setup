package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.AppService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppServiceRepository extends JpaRepository<AppService, Long> {
    Optional<AppService> findByServiceId(String serviceId);
    boolean existsByServiceId(String serviceId);
}
