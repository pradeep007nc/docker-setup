package dev.pradeep.dockerbackend.auth.service;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.AppUser;
import dev.pradeep.dockerbackend.auth.model.OtpRequest;
import dev.pradeep.dockerbackend.auth.repository.AppServiceRepository;
import dev.pradeep.dockerbackend.auth.repository.AppUserRepository;
import dev.pradeep.dockerbackend.auth.repository.OtpRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private AppUserRepository userRepository;
    @Autowired private AppServiceRepository serviceRepository;
    @Autowired private OtpRequestRepository otpRequestRepository;

    /**
     * Generates a 6-digit OTP for the user+service pair and persists it.
     * Returns the plain OTP in the response (mock behaviour — in production this
     * would be sent via SMS/email, never returned over the API).
     */
    @Transactional
    public Map<String, Object> generateOtp(String username, String serviceId) {
        AppService service = serviceRepository.findByServiceId(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

        AppUser user = userRepository.findByUsernameAndService(username, service)
                .orElseThrow(() -> new IllegalArgumentException("User not found in service: " + serviceId));

        if (!user.isActive()) {
            throw new IllegalStateException("User account is disabled");
        }

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setUser(user);
        otpRequest.setService(service);
        otpRequest.setOtpCode(otp);
        otpRequest.setExpiresAt(expiresAt);
        otpRequestRepository.save(otpRequest);

        return Map.of(
                "otp", otp,                    // returned only because this is a mock
                "username", username,
                "serviceId", serviceId,
                "expiresIn", OTP_EXPIRY_MINUTES * 60,  // seconds
                "expiresAt", expiresAt.toString(),
                "note", "In production this OTP would be sent via SMS/email, not returned here"
        );
    }

    /**
     * Validates the OTP. On success returns the user and service so AuthService
     * can issue the JWT and write the audit log.
     */
    @Transactional
    public OtpValidationResult validateOtp(String username, String serviceId, String otpCode) {
        AppService service = serviceRepository.findByServiceId(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

        AppUser user = userRepository.findByUsernameAndService(username, service)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.isActive()) {
            throw new IllegalStateException("User account is disabled");
        }

        OtpRequest otpRequest = otpRequestRepository
                .findTopByUserAndServiceAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        user, service, LocalDateTime.now())
                .orElseThrow(() -> new IllegalArgumentException("No valid OTP found — generate a new one"));

        if (!otpRequest.getOtpCode().equals(otpCode)) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        otpRequest.setUsed(true);
        otpRequestRepository.save(otpRequest);

        return new OtpValidationResult(user, service);
    }

    public record OtpValidationResult(AppUser user, AppService service) {}
}
