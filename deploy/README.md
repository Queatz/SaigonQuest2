# Deploy

Two scripts build on this machine and publish to a Debian/Ubuntu host. They use nginx for the app and Traefik for TLS. They do not use Docker.

Deploy the API first, then the web app. DNS for both domains must already point at the hosts, and ports 80 and 443 must be reachable. The SSH user needs a key and passwordless sudo.

```bash
cp .env.prod.example .env.prod
cp deploy/server.env.example deploy/server.env
cp deploy/web.env.example deploy/web.env
# edit the three files, then:
./deploy/server.sh
./deploy/web.sh
```

Re-running either script does not reinitialize the host. It does not drop the ArangoDB database, reset the root password, delete uploads, or replace Traefik certificates. `--skip-init` also skips package installation, firewall changes, nginx/Traefik setup, and ArangoDB user creation. It still uploads the new build, restarts the API when that script is used, and runs the health check.

Traefik is downloaded on this machine and uploaded. The host does not need to reach GitHub. Signing keys are imported without a terminal, so `gpg` does not prompt.

If SSH reports that the host key changed, the script stops. Confirm the new fingerprint, remove the old entry, and rerun:

```bash
ssh-keygen -f "$HOME/.ssh/known_hosts" -R example.com
```

## How the web knows the server domain

The browser keeps calling `/api` and `/media` on the web origin. That matches local webpack, and it keeps the session cookie first-party (`SameSite=Lax`).

`deploy/web.sh` writes the server domain into two places:

- nginx `proxy_pass`, so `/api` and `/media` reach the API
- `/sq-config.js`, loaded before the app, as `window.SAIGON_SERVER_DOMAIN`

Leave `API_MODE=proxy` unless the browser must call the API host directly. `direct` sets `window.SAIGON_SERVER_ORIGIN` and requires the same mode on the server so CORS and `COOKIE_SAMESITE=None` are configured. Proxy is the one that works with the current cookie settings without extra changes.

`SERVER_UPSTREAM` defaults to `https://SERVER_DOMAIN`. On one machine, set it to `http://127.0.0.1:8088` so the web nginx talks to the API nginx locally.

## Server

`deploy/server.sh` builds `:server:shadowJar`, uploads it to `/opt/saigonquest/saigonquest-server.jar`, and copies `.env.prod` to `/opt/saigonquest/.env`.

CORS is not hardcoded. Set `CORS_ALLOWED_HOSTS` in `.env.prod` (`https://town.example.com,localhost:3000`). If that key is missing and `WEB_DOMAIN` is set, the script adds it. Dev still allows `localhost:3000` and `127.0.0.1:3000` when the variable is unset.

The script creates the ArangoDB user and database from `.env.prod` with `arangosh` when they are missing, and grants `rw`. It does not drop an existing database. The official 3.12 signing key expired and has no published replacement, so apt trusts that HTTPS repository without a signature until a valid key is available. `ARANGO_ROOT_PASSWORD` in `deploy/server.env` is only used for that admin connection. It is not copied into the app `.env`. If ArangoDB is already installed, its data directory and root password are left alone; the password in `deploy/server.env` must then match the existing root user.

nginx proxies every path, including WebSocket upgrades and SSE, to the app on `127.0.0.1`. Traefik terminates TLS on 443 and flushes streaming responses. The firewall opens 80 and 443. Those are the WebSocket and SSE ports; the app does not listen on a separate public port. ArangoDB stays on localhost.

The final check is `https://SERVER_DOMAIN/api/health`. The web script then checks the homepage, `sq-config.js`, and `https://WEB_DOMAIN/api/health`.

Follow production API logs with `./deploy/logs.sh`. Pass `-n 100` to print the latest 100 lines and exit instead of following.
