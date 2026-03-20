package dev.pradeep.dockerbackend.auth.config;

import dev.pradeep.dockerbackend.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize / @PostAuthorize on controllers
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/auth/login", "/auth/register/service", "/auth/register/user").permitAll()
                // OTP endpoints — no prior auth needed
                .requestMatchers("/auth/otp/**").permitAll()
                // Token validation can be called by other services
                .requestMatchers("/auth/token/validate").permitAll()
                // Admin endpoints — admin key enforced at controller level, not Spring Security level
                .requestMatchers("/auth/admin/**").permitAll()
                // Public resource endpoint
                .requestMatchers("/api/public/**").permitAll()
                // Role-protected admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // All other /api/** require authentication; fine-grained checks via @RequiresPermission
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
