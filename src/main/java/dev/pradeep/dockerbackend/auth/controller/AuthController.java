package dev.pradeep.dockerbackend.auth.controller;

import dev.pradeep.dockerbackend.auth.dto.LoginRequest;
import dev.pradeep.dockerbackend.auth.dto.RegisterServiceRequest;
import dev.pradeep.dockerbackend.auth.dto.RegisterUserRequest;
import dev.pradeep.dockerbackend.auth.dto.TokenResponse;
import dev.pradeep.dockerbackend.auth.model.LoginAudit;
import dev.pradeep.dockerbackend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private AuthService authService;

    @Value("${auth.master-admin-key}")
    private String masterAdminKey;

    // ── Login ────────────────────────────────────────────────────────────────

    /**
     * POST /auth/login
     * Body: { "username": "alice", "password": "password123", "serviceId": "service-a" }
     * Every attempt (success or failure) is written to the login_audit table.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        TokenResponse response = authService.login(request, extractIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    // ── Token Introspection ───────────────────────────────────────────────────

    /**
     * POST /auth/token/validate
     * Body: { "token": "<jwt>" }
     * Returns valid/expired/invalid with full claims — for use by other services.
     */
    @PostMapping("/token/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Token is required"));
        }
        return ResponseEntity.ok(authService.validateToken(token));
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /** POST /auth/register/service  — Header: X-Admin-Key */
    @PostMapping("/register/service")
    public ResponseEntity<Map<String, String>> registerService(
            @RequestBody RegisterServiceRequest request,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        authService.registerService(request);
        return ResponseEntity.ok(Map.of("message", "Service registered: " + request.getServiceId()));
    }

    /** POST /auth/register/user  — Header: X-Admin-Key */
    @PostMapping("/register/user")
    public ResponseEntity<Map<String, String>> registerUser(
            @RequestBody RegisterUserRequest request,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        authService.registerUser(request);
        return ResponseEntity.ok(Map.of("message", "User registered: " + request.getUsername()));
    }

    // ── User Management ───────────────────────────────────────────────────────

    /**
     * PATCH /auth/admin/users/disable
     * Body: { "username": "bob", "serviceId": "service-a" }
     * Header: X-Admin-Key
     * Disabled users cannot log in; existing tokens remain valid until expiry.
     */
    @PatchMapping("/admin/users/disable")
    public ResponseEntity<Map<String, String>> disableUser(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        authService.setUserActive(body.get("username"), body.get("serviceId"), false);
        return ResponseEntity.ok(Map.of("message", "User disabled: " + body.get("username")));
    }

    /**
     * PATCH /auth/admin/users/enable
     * Body: { "username": "bob", "serviceId": "service-a" }
     * Header: X-Admin-Key
     */
    @PatchMapping("/admin/users/enable")
    public ResponseEntity<Map<String, String>> enableUser(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        authService.setUserActive(body.get("username"), body.get("serviceId"), true);
        return ResponseEntity.ok(Map.of("message", "User enabled: " + body.get("username")));
    }

    // ── Audit ─────────────────────────────────────────────────────────────────

    /**
     * GET /auth/admin/audit?serviceId=service-a&username=alice&page=0&size=20
     * Returns paginated login audit records for a service (or filtered by user).
     * Header: X-Admin-Key
     */
    @GetMapping("/admin/audit")
    public ResponseEntity<?> getAuditLog(
            @RequestParam String serviceId,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        Page<LoginAudit> result = authService.getAuditLog(username, serviceId, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    /**
     * GET /auth/admin/audit/last-login?username=alice&serviceId=service-a
     * Returns the most recent successful login for a user.
     * Header: X-Admin-Key
     */
    @GetMapping("/admin/audit/last-login")
    public ResponseEntity<?> getLastLogin(
            @RequestParam String username,
            @RequestParam String serviceId,
            @RequestHeader("X-Admin-Key") String adminKey) {

        if (!masterAdminKey.equals(adminKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid admin key"));
        }
        Optional<LoginAudit> last = authService.getLastLogin(username, serviceId);
        return last.map(ResponseEntity::ok)
                   .orElseGet(() -> ResponseEntity.ok(null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
