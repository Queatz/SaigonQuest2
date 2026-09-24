This is a Kotlin Multiplatform project targeting Web, Server.

* [/app/sharedLogic](./app/sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./app/sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/app/webApp](./app/webApp) is the Saigon.Town Kotlin/JS app. Routes are `/` (feed), `/new`, and `/profile`.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application. It talks to ArangoDB.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- ArangoDB (local):
    ```shell
    docker compose up -d
    ```
  The Compose root user is `root` / `saigonquest`. [`.env.example`](./.env.example) uses `saigonquest`; create that user or set `ARANGO_USER=root`.
- Server: `./gradlew :server:run`
    - `GET /api/health` — ArangoDB connection status
    - `GET /api/posts` — public feed. Poster phone numbers are not included.
    - `POST /api/posts/{id}/contact` — returns a Zalo URL. Logged-out visitors can call it; it is rate-limited.
    - Copy [`.env.example`](./.env.example) to `.env` and set `QWEN_API_KEY` for avatar generation.
    - The Compose file creates the Arango root user `root` / `saigonquest`. If `/api/health` is degraded, set `ARANGO_USER=root` or create the `saigonquest` user.
    - Optional: set `SPA_DIR` to a Kotlin/JS production output folder to serve the web app with History API fallback.
- Web app: `./gradlew :app:webApp:jsBrowserDevelopmentRun`
  Webpack dev server listens on [http://localhost:3000](http://localhost:3000), falls back to `index.html`, and proxies `/api` and `/media` to the server.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
