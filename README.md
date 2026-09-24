This is a Kotlin Multiplatform project targeting Web, Server.

* [/app/sharedLogic](./app/sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./app/sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/app/webApp](./app/webApp) is a Kotlin/JS single-page application. It uses the HTML5 History API for client-side
  routes (`/`, `/about`, and a not-found fallback) and depends on [sharedLogic](./app/sharedLogic).

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
  Default credentials match [`.env.example`](./.env.example): user `root`, password `saigonquest`, database `saigonquest`.
- Server: `./gradlew :server:run`
    - `GET /api/hello` — sample greeting
    - `GET /api/health` — ArangoDB connection status
    - Optional: set `SPA_DIR` to a Kotlin/JS production output folder to serve the web app with History API fallback
- Web app: `./gradlew :app:webApp:jsBrowserDevelopmentRun`
  Webpack dev server listens on [http://localhost:3000](http://localhost:3000) and falls back to `index.html` for SPA routes.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
