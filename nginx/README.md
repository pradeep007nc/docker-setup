# Nginx — How It Works in This Project

## What is Nginx doing here?

Without Nginx, your Spring Boot app is directly exposed to the internet on port 8080.
That means anyone can hit `http://yourserver:8080` directly.

With Nginx sitting in front:
- The outside world only talks to Nginx (ports 80 and 443)
- Nginx forwards the request to Spring Boot internally
- Spring Boot is never directly reachable from outside

```
Browser/Client
      │
      │  https://localhost  (port 443)
      ▼
  [ Nginx ]  ◄── reads nginx.conf
      │
      │  http://backend:8080  (internal Docker network only)
      ▼
  [ Spring Boot ]
      │
      │  db:3306  (internal Docker network only)
      ▼
  [ MySQL ]
```

---

## Directory Structure

```
nginx/
├── nginx.conf        ← The main config file. You edit this.
├── certs/
│   ├── fullchain.pem ← SSL Certificate (public). Sent to browsers.
│   └── privkey.pem   ← SSL Private Key (secret). Never share or commit this.
└── README.md         ← This file
```

---

## nginx.conf — Line by Line

### Top-level settings

```nginx
worker_processes auto;
```
Nginx uses multiple worker processes to handle requests.
`auto` means: use one worker per CPU core. Fine to leave this alone.

```nginx
events {
    worker_connections 1024;
}
```
Each worker can handle up to 1024 simultaneous connections.
For a dev/small production setup this is more than enough.

---

### The `http {}` block

Everything inside `http {}` deals with HTTP and HTTPS traffic.

```nginx
include       /etc/nginx/mime.types;
default_type  application/octet-stream;
```
Tells Nginx what Content-Type to send for different file extensions
(e.g. `.html` → `text/html`, `.json` → `application/json`).
Your Spring Boot app sets its own Content-Type, so this mostly doesn't matter here.

```nginx
sendfile on;
keepalive_timeout 65;
```
Performance settings. `sendfile` speeds up file serving.
`keepalive_timeout` keeps a connection open for 65 seconds
so the browser can reuse it for multiple requests.

---

### Upstream block

```nginx
upstream spring_backend {
    server backend:8080;
}
```

This defines a named group called `spring_backend`.
`backend` is the **Docker service name** from `docker-compose.yml`.
Docker's internal DNS resolves `backend` to the Spring Boot container's IP automatically.
Port `8080` is what Spring Boot listens on inside the container.

You reference this group later with `proxy_pass http://spring_backend;`

---

### Server block 1 — HTTP redirect (port 80)

```nginx
server {
    listen 80;
    server_name localhost;
    return 301 https://$host$request_uri;
}
```

- `listen 80` — this block handles all traffic on port 80 (plain HTTP)
- `server_name localhost` — only matches requests for `localhost`
  - In production replace `localhost` with your domain e.g. `example.com`
- `return 301 https://$host$request_uri` — immediately redirects the client to HTTPS
  - `301` is a permanent redirect
  - `$host` = the domain name from the request (e.g. `localhost`)
  - `$request_uri` = the path + query string (e.g. `/api/users?page=1`)
  - So `http://localhost/api/users` becomes `https://localhost/api/users`

---

### Server block 2 — HTTPS (port 443)

```nginx
server {
    listen 443 ssl;
    server_name localhost;
```

- `listen 443 ssl` — handles HTTPS traffic
- `server_name localhost` — same as above, replace with your domain in production

#### SSL Certificate settings

```nginx
    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
```

These paths are **inside the Nginx container**.
In `docker-compose.yml` we mount the local `./nginx/certs/` folder into
`/etc/nginx/certs/` inside the container:

```yaml
volumes:
  - ./nginx/certs:/etc/nginx/certs:ro   # :ro means read-only
```

So the files at `nginx/certs/fullchain.pem` on your machine
become `/etc/nginx/certs/fullchain.pem` inside Nginx.

```nginx
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;
```

- `ssl_protocols` — only allow modern, secure TLS versions. TLS 1.0 and 1.1 are disabled.
- `ssl_ciphers` — only use strong encryption algorithms. `!aNULL` and `!MD5` block known weak ones.
- `ssl_session_cache` — cache TLS handshake results for 10 minutes so repeat visitors connect faster.

#### Location block — the actual proxy

```nginx
    location / {
        proxy_pass         http://spring_backend;
```

- `location /` — matches **every** request path (`/api/...`, `/health`, `/`, etc.)
- `proxy_pass http://spring_backend` — forward the request to the upstream group defined earlier
  - Nginx connects to `backend:8080` inside Docker and forwards the request

```nginx
        proxy_http_version 1.1;
```
Use HTTP/1.1 between Nginx and Spring Boot (required for keep-alive connections).

```nginx
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
```

When Nginx forwards a request to Spring Boot, the original request information
is lost (Spring Boot only sees Nginx as the caller). These headers pass that info along:

| Header | What it tells Spring Boot |
|---|---|
| `Host` | The domain the client originally requested |
| `X-Real-IP` | The client's real IP address |
| `X-Forwarded-For` | Full chain of IPs if there are multiple proxies |
| `X-Forwarded-Proto` | Whether the original request was `http` or `https` |

Spring Boot can read these headers to know the real client IP and protocol.

```nginx
        proxy_read_timeout    90s;
        proxy_connect_timeout 90s;
```

If Spring Boot takes longer than 90 seconds to respond, Nginx gives up and returns a 504 error.
Increase this if you have endpoints that run long background tasks.

---

## The SSL Certificates

### What are they?

| File | What it is | Who sees it |
|---|---|---|
| `fullchain.pem` | The SSL certificate — proves your server's identity | Sent to every browser |
| `privkey.pem` | The private key — used to encrypt/decrypt traffic | **Never share. Never commit.** |

### For local testing (self-signed — already done)

The cert in `nginx/certs/` was generated with:

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx/certs/privkey.pem \
  -out nginx/certs/fullchain.pem \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

Because it is self-signed (not issued by a trusted authority like Let's Encrypt),
browsers will show a warning. That is normal for local development.

**To bypass the browser warning:**
- Chrome/Edge: click anywhere on the page, type `thisisunsafe`
- Firefox: click "Advanced" → "Accept the Risk and Continue"
- curl: use the `-k` flag → `curl -k https://localhost/api/...`

### To permanently trust the cert on your Mac (no more warnings)

```bash
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain \
  nginx/certs/fullchain.pem
```

Restart the browser after running this.

### For production (real domain + Let's Encrypt)

Replace the contents of `nginx/certs/` with the real certs from Let's Encrypt:

```
/etc/letsencrypt/live/yourdomain.com/fullchain.pem  →  nginx/certs/fullchain.pem
/etc/letsencrypt/live/yourdomain.com/privkey.pem    →  nginx/certs/privkey.pem
```

Also update `server_name localhost;` in `nginx.conf` to `server_name yourdomain.com;`

---

## How docker-compose.yml wires Nginx in

```yaml
nginx:
  image: nginx:alpine          # Official Nginx image, Alpine = small size
  restart: always              # Auto-restart if it crashes
  ports:
    - "80:80"                  # host port 80  → nginx port 80
    - "443:443"                # host port 443 → nginx port 443
  volumes:
    - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro   # Mount our config
    - ./nginx/certs:/etc/nginx/certs:ro             # Mount our certs
  depends_on:
    - backend                  # Start nginx only after backend is up
```

The `backend` service no longer has ports exposed to the host — all traffic must go through Nginx.

---

## Common tasks

### Reload nginx config without restarting the container

```bash
docker compose exec nginx nginx -s reload
```

### Test if your nginx.conf has syntax errors

```bash
docker compose exec nginx nginx -t
```

### View nginx logs

```bash
docker compose logs nginx
docker compose logs nginx -f   # follow / live tail
```

### View Spring Boot logs

```bash
docker compose logs backend -f
```