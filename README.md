# Dockerized Spring Boot Backend

A containerized Spring Boot authentication backend integrated with MySQL using Docker and Docker Compose. The system supports **multi-service authentication** — multiple independent services can register, each with its own users, roles, and permissions, all managed through a central auth server.

---

## System Architecture

```
┌─────────────────────────────────────────────────┐
│                  Auth Server                    │
│                                                 │
│  service-a ──► roles/permissions ──► users      │
│  service-b ──► roles/permissions ──► users      │
│  service-n ──► roles/permissions ──► users      │
│                                                 │
│  JWT issued per user, scoped to their service   │
└─────────────────────────────────────────────────┘
```

Each service is an isolated tenant. Users, roles, and permissions are always scoped to a service — a user in `service-a` cannot authenticate against `service-b`.

---

## Docker Setup

### Multi-Container Architecture

| Container | Purpose |
|---|---|
| `backend` | Spring Boot application |
| `db` | MySQL database |

Docker Compose creates a private network so `backend` talks to `db` by hostname.

### Build and Run

```bash
# Build images and start all containers
docker-compose up --build

# Run in background
docker-compose up -d

# Stop containers (data is preserved in volume)
docker-compose down

# Stop and delete all data
docker-compose down -v
```

### View Logs

```bash
docker-compose logs -f
```

### Enter the running backend container

```bash
docker-compose exec backend /bin/sh
```

---

## Configuration

All sensitive values are injected via environment variables. The defaults in `application.properties` are for local development only — always override in production.

| Environment Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/dockerdb` | Database connection URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `rootpassword` | DB password |
| `JWT_SECRET` | *(base64 default)* | JWT signing secret (min 256-bit) |
| `JWT_EXPIRATION` | `86400000` | Token expiry in milliseconds (24h) |
| `MASTER_ADMIN_KEY` | `master-secret-key` | Master key for all admin API calls |

Set these in a `.env` file for multi-environment setups:

```env
MASTER_ADMIN_KEY=your-secure-admin-key
JWT_SECRET=your-base64-encoded-secret
SPRING_DATASOURCE_PASSWORD=your-db-password
```

---

## Seed Data (Auto-loaded on startup)

The application seeds two services with users on first boot:

### service-a (Service Alpha)

| User | Password | Role | Permissions |
|---|---|---|---|
| `alice` | `password123` | `ROLE_ADMIN` | `READ_ORDERS`, `WRITE_ORDERS`, `DELETE_ORDERS` |
| `bob` | `password123` | `ROLE_USER` | `READ_ORDERS` |

### service-b (Service Beta)

| User | Password | Role | Permissions |
|---|---|---|---|
| `charlie` | `password123` | `ROLE_ADMIN` | `READ_REPORTS`, `READ_USERS`, `MANAGE_USERS`, `DELETE_USERS` |
| `diana` | `password123` | `ROLE_USER` | `READ_REPORTS`, `READ_USERS` |

---

## Auth API Reference

All admin endpoints require the header:
```
X-Admin-Key: <MASTER_ADMIN_KEY>
```

All protected endpoints require:
```
Authorization: Bearer <jwt-token>
```

---

### 1. Register a Service

Before registering users, a service must be registered. Services are the top-level tenants.

```
POST /auth/register/service
Header: X-Admin-Key: master-secret-key
```

```json
{
  "serviceId": "order-service",
  "serviceName": "Order Management Service",
  "clientSecret": "a-strong-client-secret"
}
```

**Response:**
```json
{ "message": "Service registered: order-service" }
```

> `serviceId` must be unique across the system. `clientSecret` is stored hashed.

---

### 2. Register a User

Users are always scoped to a specific service. Roles and permissions are assigned at registration time — they must already exist in the database for that service.

```
POST /auth/register/user
Header: X-Admin-Key: master-secret-key
```

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "serviceId": "order-service",
  "roles": ["ROLE_ADMIN"],
  "permissions": ["READ_ORDERS", "WRITE_ORDERS"]
}
```

**Response:**
```json
{ "message": "User registered: alice" }
```

> `roles` and `permissions` are optional. If provided, they must exist in the database scoped to the given `serviceId`.

---

### 3. Login (Password)

```
POST /auth/login
```

```json
{
  "username": "alice",
  "password": "password123",
  "serviceId": "order-service"
}
```

**Response:**
```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "roles": ["ROLE_ADMIN"],
  "permissions": ["READ_ORDERS", "WRITE_ORDERS"],
  "serviceId": "order-service",
  "authMethod": "PASSWORD",
  "expiresAt": "2026-03-22T10:00:00"
}
```

Every login attempt (success or failure) is recorded in the `login_audit` table.

---

### 4. Login (OTP — Two Step)

**Step 1 — Request OTP:**

```
POST /auth/otp/generate
```

```json
{
  "username": "alice",
  "serviceId": "order-service"
}
```

> In development, the OTP is returned in the response. In production it would be delivered via SMS/email.

**Step 2 — Submit OTP to get JWT:**

```
POST /auth/otp/login
```

```json
{
  "username": "alice",
  "serviceId": "order-service",
  "otp": "482910"
}
```

OTPs expire after 5 minutes and are single-use.

---

### 5. Validate a Token

Used by downstream services to verify tokens issued by this auth server.

```
POST /auth/token/validate
```

```json
{ "token": "<jwt>" }
```

**Response (valid):**
```json
{
  "valid": true,
  "reason": "OK",
  "username": "alice",
  "userId": 1,
  "serviceId": "order-service",
  "roles": ["ROLE_ADMIN"],
  "permissions": ["READ_ORDERS", "WRITE_ORDERS"],
  "authMethod": "PASSWORD",
  "expiresAt": "2026-03-22T10:00:00"
}
```

**Response (expired):**
```json
{
  "valid": false,
  "reason": "TOKEN_EXPIRED",
  "username": "alice",
  ...
}
```

---

### 6. Disable / Enable a User

Disabled users cannot log in. Existing tokens remain valid until they expire naturally.

```
PATCH /auth/admin/users/disable
Header: X-Admin-Key: master-secret-key
```

```json
{ "username": "bob", "serviceId": "order-service" }
```

```
PATCH /auth/admin/users/enable
Header: X-Admin-Key: master-secret-key
```

```json
{ "username": "bob", "serviceId": "order-service" }
```

---

### 7. Audit Log

**Paginated login history for a service:**

```
GET /auth/admin/audit?serviceId=order-service&page=0&size=20
Header: X-Admin-Key: master-secret-key
```

Filter by user:
```
GET /auth/admin/audit?serviceId=order-service&username=alice
```

**Last successful login for a user:**

```
GET /auth/admin/audit/last-login?username=alice&serviceId=order-service
Header: X-Admin-Key: master-secret-key
```

---

## Permission Enforcement

Endpoints are protected using the `@RequiresPermission` annotation:

```java
@GetMapping("/orders")
@RequiresPermission("READ_ORDERS")
public ResponseEntity<?> getOrders() { ... }

// Require ALL listed permissions
@DeleteMapping("/orders/{id}")
@RequiresPermission(value = {"WRITE_ORDERS", "DELETE_ORDERS"}, requireAll = true)
public ResponseEntity<?> deleteOrder(@PathVariable Long id) { ... }
```

Permissions are extracted from the JWT on every request — no database call at runtime. Roles are enforced at the Spring Security filter chain level.

---

## Full Setup Walkthrough

Here is the complete sequence to onboard a new service from scratch:

```bash
BASE=http://localhost:8080
KEY=master-secret-key

# 1. Register the service
curl -s -X POST $BASE/auth/register/service \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $KEY" \
  -d '{"serviceId":"order-service","serviceName":"Order Service","clientSecret":"strong-secret"}'

# NOTE: Roles and permissions for this service must be seeded directly into the
# database (roles and permissions tables) before registering users that use them.
# There is currently no API endpoint for creating roles/permissions — this is by design
# so that permission definitions are controlled at the infrastructure level, not via API.

# 2. Register a user (roles/permissions must already exist in DB for this service)
curl -s -X POST $BASE/auth/register/user \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $KEY" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "serviceId": "order-service",
    "roles": ["ROLE_ADMIN"],
    "permissions": ["READ_ORDERS", "WRITE_ORDERS"]
  }'

# 3. Login
curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","serviceId":"order-service"}'

# 4. Use the token
curl -s $BASE/your-protected-endpoint \
  -H "Authorization: Bearer <token-from-step-3>"
```

---

## Troubleshooting

| Issue | Fix |
|---|---|
| Backend can't reach DB | Check `docker-compose logs db` — DB may still be starting up |
| `Invalid admin key` | Ensure `X-Admin-Key` matches `MASTER_ADMIN_KEY` env var |
| `Service not found` | Register the service first before registering users |
| `Role not found` | Seed the role into the DB for the target service before registering users |
| Port 8080 in use | Change the host port in `docker-compose.yml`: `"9090:8080"` |
| Connect to DB from host | Use `localhost:3306` from IntelliJ/DBeaver while containers are running |
