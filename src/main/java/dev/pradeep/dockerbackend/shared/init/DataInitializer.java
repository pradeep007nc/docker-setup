package dev.pradeep.dockerbackend.shared.init;

import dev.pradeep.dockerbackend.auth.model.*;
import dev.pradeep.dockerbackend.auth.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the database with two services, their roles/permissions, and four test users.
 * Only inserts if data is not already present (safe for restarts).
 *
 * Seeded users:
 *   alice   / password123 → service-a → ROLE_ADMIN + READ_ORDERS + WRITE_ORDERS + DELETE_ORDERS
 *   bob     / password123 → service-a → ROLE_USER  + READ_ORDERS
 *   charlie / password123 → service-b → ROLE_ADMIN + READ_REPORTS + READ_USERS + MANAGE_USERS + DELETE_USERS
 *   diana   / password123 → service-b → ROLE_USER  + READ_REPORTS + READ_USERS
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private AppServiceRepository serviceRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private PermissionRepository permissionRepo;
    @Autowired private AppUserRepository userRepo;
    @Autowired private UserRoleRepository userRoleRepo;
    @Autowired private UserPermissionRepository userPermissionRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedServiceA();
        seedServiceB();
    }

    private void seedServiceA() {
        if (serviceRepo.existsByServiceId("service-a")) return;

        AppService svcA = new AppService();
        svcA.setServiceId("service-a");
        svcA.setServiceName("Service Alpha");
        svcA.setClientSecret(passwordEncoder.encode("secret-alpha"));
        serviceRepo.save(svcA);

        Role adminA = createRole("ROLE_ADMIN", "Administrator for Service Alpha", svcA);
        Role userA  = createRole("ROLE_USER",  "Regular user for Service Alpha",  svcA);

        Permission readOrders   = createPermission("READ_ORDERS",   "View orders",   svcA);
        Permission writeOrders  = createPermission("WRITE_ORDERS",  "Create orders", svcA);
        Permission deleteOrders = createPermission("DELETE_ORDERS", "Delete orders", svcA);

        // alice: ROLE_ADMIN + all order permissions
        AppUser alice = createUser("alice", "alice@example.com", svcA);
        assignRole(alice, adminA, svcA);
        assignPermission(alice, readOrders,   svcA);
        assignPermission(alice, writeOrders,  svcA);
        assignPermission(alice, deleteOrders, svcA);

        // bob: ROLE_USER + read only
        AppUser bob = createUser("bob", "bob@example.com", svcA);
        assignRole(bob, userA, svcA);
        assignPermission(bob, readOrders, svcA);
    }

    private void seedServiceB() {
        if (serviceRepo.existsByServiceId("service-b")) return;

        AppService svcB = new AppService();
        svcB.setServiceId("service-b");
        svcB.setServiceName("Service Beta");
        svcB.setClientSecret(passwordEncoder.encode("secret-beta"));
        serviceRepo.save(svcB);

        Role adminB = createRole("ROLE_ADMIN",     "Administrator for Service Beta", svcB);
        Role userB  = createRole("ROLE_USER",      "Regular user for Service Beta",  svcB);
        createRole("ROLE_MODERATOR", "Moderator for Service Beta", svcB);

        Permission readReports  = createPermission("READ_REPORTS",  "View reports",        svcB);
        Permission readUsers    = createPermission("READ_USERS",    "View user list",       svcB);
        Permission manageUsers  = createPermission("MANAGE_USERS",  "Manage users",         svcB);
        Permission deleteUsers  = createPermission("DELETE_USERS",  "Delete users",         svcB);

        // charlie: ROLE_ADMIN + all service-b permissions
        AppUser charlie = createUser("charlie", "charlie@example.com", svcB);
        assignRole(charlie, adminB, svcB);
        assignPermission(charlie, readReports, svcB);
        assignPermission(charlie, readUsers,   svcB);
        assignPermission(charlie, manageUsers, svcB);
        assignPermission(charlie, deleteUsers, svcB);

        // diana: ROLE_USER + read-only
        AppUser diana = createUser("diana", "diana@example.com", svcB);
        assignRole(diana, userB, svcB);
        assignPermission(diana, readReports, svcB);
        assignPermission(diana, readUsers,   svcB);
    }

    private AppService getOrCreateService(String serviceId) {
        return serviceRepo.findByServiceId(serviceId).orElseThrow();
    }

    private Role createRole(String name, String description, AppService service) {
        Role role = new Role();
        role.setRoleName(name);
        role.setDescription(description);
        role.setService(service);
        return roleRepo.save(role);
    }

    private Permission createPermission(String name, String description, AppService service) {
        Permission p = new Permission();
        p.setPermissionName(name);
        p.setDescription(description);
        p.setService(service);
        return permissionRepo.save(p);
    }

    private AppUser createUser(String username, String email, AppService service) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setService(service);
        return userRepo.save(user);
    }

    private void assignRole(AppUser user, Role role, AppService service) {
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setService(service);
        userRoleRepo.save(ur);
    }

    private void assignPermission(AppUser user, Permission permission, AppService service) {
        UserPermission up = new UserPermission();
        up.setUser(user);
        up.setPermission(permission);
        up.setService(service);
        userPermissionRepo.save(up);
    }
}
