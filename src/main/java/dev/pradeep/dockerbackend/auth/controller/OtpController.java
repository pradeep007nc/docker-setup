package dev.pradeep.dockerbackend.auth.controller;

import dev.pradeep.dockerbackend.auth.dto.TokenResponse;
import dev.pradeep.dockerbackend.auth.service.AuthService;
import dev.pradeep.dockerbackend.auth.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/otp")
public class OtpController {

    @Autowired private OtpService otpService;
    @Autowired private AuthService authService;

    /**
     * Step 1 — Request an OTP.
     *
     * POST /auth/otp/generate
     * Body: { "username": "alice", "serviceId": "service-a" }
     *
     * Returns the OTP directly in the response (mock behaviour).
     * In production, this would send the OTP via SMS/email and return only { "message": "OTP sent" }.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateOtp(@RequestBody Map<String, String> body) {
        String username  = body.get("username");
        String serviceId = body.get("serviceId");

        if (username == null || serviceId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and serviceId are required"));
        }

        Map<String, Object> result = otpService.generateOtp(username, serviceId);
        return ResponseEntity.ok(result);
    }

    /**
     * Step 2 — Submit the OTP to receive a JWT.
     *
     * POST /auth/otp/login
     * Body: { "username": "alice", "serviceId": "service-a", "otp": "123456" }
     *
     * The issued JWT will have authMethod=OTP in its claims, which is also stored in
     * the login_audit table so you can distinguish which auth method was used.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> otpLogin(@RequestBody Map<String, String> body,
                                                   HttpServletRequest httpRequest) {
        String username  = body.get("username");
        String serviceId = body.get("serviceId");
        String otp       = body.get("otp");

        if (username == null || serviceId == null || otp == null) {
            return ResponseEntity.badRequest().build();
        }

        String ipAddress = extractIp(httpRequest);

        // OtpService validates the code and marks it as used
        OtpService.OtpValidationResult validated = otpService.validateOtp(username, serviceId, otp);

        // AuthService issues the JWT and writes the audit log
        TokenResponse response = authService.loginWithOtp(validated, ipAddress);
        return ResponseEntity.ok(response);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
