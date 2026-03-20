package dev.pradeep.dockerbackend.auth.repository;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.AppUser;
import dev.pradeep.dockerbackend.auth.model.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {

    /** Finds the latest unused, non-expired OTP for a user+service */
    Optional<OtpRequest> findTopByUserAndServiceAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            AppUser user, AppService service, LocalDateTime now);
}
