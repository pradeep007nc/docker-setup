package dev.pradeep.dockerbackend.auth.service;

import dev.pradeep.dockerbackend.auth.dto.LoginRequest;
import dev.pradeep.dockerbackend.auth.dto.RegisterServiceRequest;
import dev.pradeep.dockerbackend.auth.dto.RegisterUserRequest;
import dev.pradeep.dockerbackend.auth.dto.TokenResponse;
import dev.pradeep.dockerbackend.auth.model.*;
import dev.pradeep.dockerbackend.auth.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired private AppUserRepository userRepository;
    @Autowired private AppServiceRepository serviceRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private UserPermissionRepository userPermissionRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private LoginAuditRepository auditRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    // ── Password Login ───────────────────────────────────────────────────────

    /**
     * Authenticates via username + password, issues a JWT scoped to the service,
     * and writes a login audit record regardless of success or failure.
     */
    public TokenResponse login(LoginRequest request, String ipAddress) {
        String username = request.getUsername();
        String serviceId = request.getServiceId();

        try {
            AppService service = serviceRepository.findByServiceId(serviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

            if (!service.isActive()) {
                throw new IllegalStateException("Service is disabled: " + serviceId);
            }

            AppUser user = userRepository.findByUsernameAndService(username, service)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid credentials");
            }

            if (!user.isActive()) {
                throw new IllegalStateException("User account is disabled");
            }

            TokenResponse response = buildTokenResponse(user, service, "PASSWORD");
            recordAudit(username, serviceId, "PASSWORD", true, null, ipAddress, response.getExpiresAt());
            return response;

        } catch (Exception e) {
            recordAudit(username, serviceId, "PASSWORD", false, e.getMessage(), ipAddress, null);
            throw e;
        }
    }

    // ── OTP Login ────────────────────────────────────────────────────────────

    /**
     * Authenticates via OTP (after calling /auth/otp/generate), issues a JWT,
     * and writes a login audit record. Called by OtpController after OtpService
     * validates the code.
     */
    public TokenResponse loginWithOtp(OtpService.OtpValidationResult validated, String ipAddress) {
        AppUser user = validated.user();
        AppService service = validated.service();

        try {
            if (!user.isActive()) {
                throw new IllegalStateException("User account is disabled");
            }
            TokenResponse response = buildTokenResponse(user, service, "OTP");
            recordAudit(user.getUsername(), service.getServiceId(), "OTP", true, null, ipAddress, response.getExpiresAt());
            return response;
        } catch (Exception e) {
            recordAudit(user.getUsername(), service.getServiceId(), "OTP", false, e.getMessage(), ipAddress, null);
            throw e;
        }
    }

    // ── Token Introspection ───────────────────────────────────────────────────

    /**
     * Remote token validation for other services. Differentiates expired vs invalid.
     */
    public Map<String, Object> validateToken(String token) {
        TokenService.TokenStatus status = tokenService.getStatus(token);

        if (status == TokenService.TokenStatus.INVALID) {
            return Map.of("valid", false, "reason", "INVALID_TOKEN");
        }

        // For expired tokens, we can still read the claims to tell callers who it belonged to
        var claims = tokenService.parseExpiredToken(token);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", status == TokenService.TokenStatus.VALID);
        result.put("reason", status == TokenService.TokenStatus.EXPIRED ? "TOKEN_EXPIRED" : "OK");
        result.put("username", claims.getSubject());
        result.put("userId", claims.get("userId"));
        result.put("serviceId", claims.get("serviceId"));
        result.put("roles", claims.get("roles"));
        result.put("permissions", claims.get("permissions"));
        result.put("authMethod", claims.get("authMethod"));
        result.put("expiresAt", claims.getExpiration().toString());
        return result;
    }

    // ── Disable / Enable User ────────────────────────────────────────────────

    @Transactional
    public void setUserActive(String username, String serviceId, boolean active) {
        AppService service = serviceRepository.findByServiceId(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));
        AppUser user = userRepository.findByUsernameAndService(username, service)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setActive(active);
        userRepository.save(user);
    }

    // ── Audit Query ──────────────────────────────────────────────────────────

    public Optional<LoginAudit> getLastLogin(String username, String serviceId) {
        return auditRepository.findTopByUsernameAndServiceIdAndSuccessTrueOrderByLoginAtDesc(username, serviceId);
    }

    public Page<LoginAudit> getAuditLog(String username, String serviceId, Pageable pageable) {
        if (username != null && !username.isBlank()) {
            return auditRepository.findByUsernameAndServiceIdOrderByLoginAtDesc(username, serviceId, pageable);
        }
        return auditRepository.findByServiceIdOrderByLoginAtDesc(serviceId, pageable);
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Transactional
    public void registerService(RegisterServiceRequest request) {
        if (serviceRepository.existsByServiceId(request.getServiceId())) {
            throw new IllegalArgumentException("Service already registered: " + request.getServiceId());
        }
        AppService service = new AppService();
        service.setServiceId(request.getServiceId());
        service.setServiceName(request.getServiceName());
        service.setClientSecret(passwordEncoder.encode(request.getClientSecret()));
        serviceRepository.save(service);
    }

    @Transactional
    public void registerUser(RegisterUserRequest request) {
        AppService service = serviceRepository.findByServiceId(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + request.getServiceId()));

        if (userRepository.existsByUsernameAndService(request.getUsername(), service)) {
            throw new IllegalArgumentException("Username already exists in service: " + request.getServiceId());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setService(service);
        userRepository.save(user);

        if (request.getRoles() != null) {
            for (String roleName : request.getRoles()) {
                Role role = roleRepository.findByRoleNameAndService(roleName, service)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
                UserRole ur = new UserRole();
                ur.setUser(user); ur.setRole(role); ur.setService(service);
                userRoleRepository.save(ur);
            }
        }

        if (request.getPermissions() != null) {
            for (String permName : request.getPermissions()) {
                Permission permission = permissionRepository.findByPermissionNameAndService(permName, service)
                        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permName));
                UserPermission up = new UserPermission();
                up.setUser(user); up.setPermission(permission); up.setService(service);
                userPermissionRepository.save(up);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TokenResponse buildTokenResponse(AppUser user, AppService service, String authMethod) {
        List<String> roles = userRoleRepository.findByUserAndService(user, service)
                .stream().map(ur -> ur.getRole().getRoleName()).toList();

        List<String> permissions = userPermissionRepository.findByUserAndService(user, service)
                .stream().map(up -> up.getPermission().getPermissionName()).toList();

        String token = tokenService.generateToken(user, service, roles, permissions, authMethod);
        LocalDateTime expiresAt = tokenService.getExpiryDate(user);

        return new TokenResponse(token, "Bearer", roles, permissions, service.getServiceId(), authMethod, expiresAt);
    }

    private void recordAudit(String username, String serviceId, String authMethod,
                              boolean success, String failureReason,
                              String ipAddress, LocalDateTime tokenExpiresAt) {
        LoginAudit audit = new LoginAudit();
        audit.setUsername(username);
        audit.setServiceId(serviceId);
        audit.setAuthMethod(authMethod);
        audit.setSuccess(success);
        audit.setFailureReason(failureReason);
        audit.setIpAddress(ipAddress);
        audit.setTokenExpiresAt(tokenExpiresAt);
        auditRepository.save(audit);
    }
}
