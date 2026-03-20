package dev.pradeep.dockerbackend.auth.service;

import dev.pradeep.dockerbackend.auth.model.AppService;
import dev.pradeep.dockerbackend.auth.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class TokenService {

    public enum TokenStatus { VALID, EXPIRED, INVALID }

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * Generates a JWT embedding the user's identity, service context, roles,
     * permissions, and the auth method used (PASSWORD or OTP).
     */
    public String generateToken(AppUser user, AppService service,
                                List<String> roles, List<String> permissions,
                                String authMethod) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("serviceId", service.getServiceId())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("authMethod", authMethod)
                .issuer("dockerbackend-auth")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns VALID, EXPIRED, or INVALID — callers can give specific error messages to clients.
     */
    public TokenStatus getStatus(String token) {
        try {
            parseToken(token);
            return TokenStatus.VALID;
        } catch (ExpiredJwtException e) {
            return TokenStatus.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return TokenStatus.INVALID;
        }
    }

    /**
     * Returns the claims even for an expired token (needed for the validate endpoint
     * to still return who the token belonged to, just with expired status).
     */
    public Claims parseExpiredToken(String token) {
        try {
            return parseToken(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims(); // JJWT exposes claims even on expiry
        }
    }

    public LocalDateTime getExpiryDate(AppUser user) {
        return LocalDateTime.now().plusSeconds(expirationMs / 1000);
    }
}
