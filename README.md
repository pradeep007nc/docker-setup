1# Dockerized Spring Boot Backend

This project is a containerized Spring Boot application (`dockerbackend`) integrated with a MySQL database using Docker and Docker Compose. This guide provides a deep dive into how the Docker configuration works, the purpose of each command, and how to manage the system.

---

## 🚀 How the Docker Configuration Works

The project uses a **multi-container architecture**. Instead of installing Java or MySQL on your local machine, you use Docker to create isolated environments (containers) for each part of the system.

### The Two Pillars:
1.  **Images:** The "blueprints" for your containers. They contain the OS, runtime (Java), and your application code/dependencies.
2.  **Containers:** The "running instances" of those images.

### System Overview:
- **`backend` service:** Runs the Spring Boot application.
- **`db` service:** Runs the MySQL database.
- **Docker Network:** Docker Compose automatically creates a private network where the `backend` can talk to the `db` using the hostname `db`.
- **Docker Volumes:** Used to persist database data even if the container is deleted.

---

## 📄 Dockerfile Breakdown

The `Dockerfile` uses a **Multi-Stage Build**. This keeps the final production image small and secure by excluding build tools like Maven.

| Command | Meaning |
| :--- | :--- |
| `FROM maven:3.9.6-eclipse-temurin-17 AS build` | Starts the "Build Stage" using a Maven image with Java 17. |
| `WORKDIR /app` | Sets the working directory inside the container to `/app`. |
| `COPY pom.xml .` | Copies only the `pom.xml` first to leverage Docker's layer caching. |
| `RUN mvn dependency:go-offline -B` | Downloads all dependencies. If `pom.xml` doesn't change, Docker skips this in future builds. |
| `COPY src ./src` | Copies your source code into the container. |
| `RUN mvn package -DskipTests` | Compiles and packages the app into a `.jar` file. |
| `FROM eclipse-temurin:17-jre` | Starts the "Final Stage" using a tiny JRE image (no compiler, just the runner). |
| `COPY --from=build /app/target/*.jar app.jar` | Copies only the final artifact from the build stage to this clean image. |
| `EXPOSE 8080` | Documents that the container listens on port 8080. |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | The command that runs when the container starts. |

---

## 🎼 Docker Compose Breakdown

`docker-compose.yml` orchestrates multiple containers so they work together seamlessly.

### Services:
- **`db`:**
    - `build: ../docker-db`: Builds the database image from a local directory.
    - `environment`: Sets MySQL credentials (`MYSQL_DATABASE`, `MYSQL_USER`, etc.).
    - `volumes`: Maps `db_data` (a Docker volume) to `/var/lib/mysql` inside the container so your data isn't lost when the container stops.
    - `healthcheck`: Tests if MySQL is actually ready to accept connections.
- **`backend`:**
    - `build: .`: Builds the image using the `Dockerfile` in the current directory.
    - `depends_on`: Ensures the backend only starts after the `db` healthcheck passes.
    - `environment`: Overrides Spring Boot's `application.properties` with the container-specific database URL (`jdbc:mysql://db:3306/...`).

---

## 🛠️ How to Build and Run

### 1. Build and Start Everything
Run this command from the root directory:
```bash
docker-compose up --build
```
*The `--build` flag ensures your latest code changes are re-compiled into the image.*

### 2. Run in Detached Mode (Background)
```bash
docker-compose up -d
```

### 3. Stop and Remove Containers
```bash
docker-compose down
```
*Note: This stops the app but keeps your database data safe in the volume.*

### 4. Remove Everything (Including Data)
```bash
docker-compose down -v
```

---

## 🎨 Customization Variations

### Change Database Credentials
Update the `environment` section in `docker-compose.yml`. The Spring Boot app automatically picks these up because of the variable mapping in `application.properties`:
```properties
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
```

### Change Ports
If port `8080` is busy on your machine, change the **mapping** in `docker-compose.yml`:
```yaml
ports:
  - "9090:8080" # Host Port 9090 -> Container Port 8080
```

### Resource Limits (Production style)
You can limit how much CPU/RAM the containers use:
```yaml
backend:
  deploy:
    resources:
      limits:
        cpus: '0.5'
        memory: 512M
```

---

## 🌐 Multi-Server & Remote Networking

If you decide to move your database to a **different server** (e.g., an RDS instance on AWS or a standalone MySQL server), the internal Docker network hostname `db` will no longer work. Here is how to configure it:

### 1. Update the Connection String
In your `docker-compose.yml`, change the `SPRING_DATASOURCE_URL` from the service name (`db`) to the **Public IP** or **Domain Name** of the remote server:

```yaml
# Example for a remote server
environment:
  - SPRING_DATASOURCE_URL=jdbc:mysql://203.0.113.10:3306/dockerdb
```

### 2. External Network Configuration
If you want your container to communicate with services outside its local network, you may need to:
- **Allow Remote Access in MySQL:** Ensure your MySQL server allows connections from the backend server's IP.
- **Firewall Rules:** Open port `3306` on the database server's firewall.
- **Docker Host Networking:** In rare cases, you might use `network_mode: "host"` in `docker-compose.yml` to make the container share the host's IP directly, though this is less secure.

### 3. Using Environment Files (.env)
For multi-environment setups (Dev, Staging, Prod), create a `.env` file to keep your `docker-compose.yml` clean:
```env
# .env file
DB_HOST=my-production-db.com
DB_USER=admin
DB_PASS=securepassword
```
Then reference them in `docker-compose.yml`:
```yaml
environment:
  - SPRING_DATASOURCE_URL=jdbc:mysql://${DB_HOST}:3306/dockerdb
```

---

## 🔍 Troubleshooting
- **Logs:** View logs with `docker-compose logs -f`.
- **Database Access:** You can connect to the DB from your host machine (IntelliJ/DBeaver) using `localhost:3306`.
- **Internal Shell:** To "enter" the running backend container:
  ```bash
  docker-compose exec backend /bin/sh
  ```
