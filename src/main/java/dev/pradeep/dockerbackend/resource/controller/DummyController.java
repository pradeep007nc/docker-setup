package dev.pradeep.dockerbackend.resource.controller;

import dev.pradeep.dockerbackend.resource.annotation.RequiresPermission;
import dev.pradeep.dockerbackend.shared.model.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dummy endpoints demonstrating role-based and permission-based access control.
 *
 * Test users (seeded by DataInitializer):
 *   alice  / password123  → service-a → ROLE_ADMIN + READ_ORDERS + WRITE_ORDERS + DELETE_ORDERS
 *   bob    / password123  → service-a → ROLE_USER  + READ_ORDERS
 *   charlie/ password123  → service-b → ROLE_ADMIN + READ_REPORTS + READ_USERS + MANAGE_USERS + DELETE_USERS
 *   diana  / password123  → service-b → ROLE_USER  + READ_REPORTS + READ_USERS
 */
@RestController
@RequestMapping("/api")
public class DummyController {

    // ── Public ──────────────────────────────────────────────────────────────

    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "Hello! No authentication required.");
    }

    // ── Any authenticated user ───────────────────────────────────────────────

    @GetMapping("/user/profile")
    public Map<String, Object> myProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "username", principal.getUsername(),
                "userId", principal.getUserId(),
                "serviceId", principal.getServiceId(),
                "roles", principal.getRoles(),
                "permissions", principal.getPermissions()
        );
    }

    // ── Role-based (ROLE_ADMIN via Spring Security's hasRole) ─────────────────

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "message", "Admin dashboard — restricted to ROLE_ADMIN",
                "loggedInAs", principal.getUsername(),
                "service", principal.getServiceId()
        );
    }

    // ── Service-A: Order endpoints (permission-based via @RequiresPermission) ──

    @GetMapping("/service-a/orders")
    @RequiresPermission("READ_ORDERS")
    public Map<String, Object> getOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "serviceContext", principal.getServiceId(),
                "accessedBy", principal.getUsername(),
                "orders", List.of(
                        Map.of("id", "ORD-001", "item", "Widget A", "status", "shipped"),
                        Map.of("id", "ORD-002", "item", "Widget B", "status", "pending")
                )
        );
    }

    @PostMapping("/service-a/orders")
    @RequiresPermission("WRITE_ORDERS")
    public Map<String, String> createOrder(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "message", "Order created by " + principal.getUsername(),
                "item", body.getOrDefault("item", "unknown")
        );
    }

    @DeleteMapping("/service-a/orders/{id}")
    @RequiresPermission("DELETE_ORDERS")
    public Map<String, String> deleteOrder(@PathVariable String id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("message", "Order " + id + " deleted by " + principal.getUsername());
    }

    // ── Service-B: Report and user management endpoints ──────────────────────

    @GetMapping("/service-b/reports")
    @RequiresPermission("READ_REPORTS")
    public Map<String, Object> getReports(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "serviceContext", principal.getServiceId(),
                "accessedBy", principal.getUsername(),
                "reports", List.of("Q1-2025-Summary.pdf", "Q2-2025-Summary.pdf")
        );
    }

    @GetMapping("/service-b/users")
    @RequiresPermission(value = {"READ_USERS", "MANAGE_USERS"}, requireAll = false)
    public Map<String, Object> listUsers(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "accessedBy", principal.getUsername(),
                "users", List.of("alice", "bob", "charlie", "diana")
        );
    }

    @DeleteMapping("/service-b/users/{id}")
    @RequiresPermission(value = {"MANAGE_USERS", "DELETE_USERS"}, requireAll = true)
    public Map<String, String> deleteUser(@PathVariable Long id,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("message", "User " + id + " deleted by " + principal.getUsername());
    }
}
