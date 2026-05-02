# Merry Match — Backend

REST API for the **Merry Match** application, built with Spring Boot. Uses PostgreSQL and integrates with supporting services such as Supabase and JWT-based auth.

## Frontend

The web app (Vue 3 + Vite) lives in a separate repository:

- **GitHub:** [polarisden/merry-match-project](https://github.com/polarisden/merry-match-project.git)
- **Clone:** `git clone https://github.com/polarisden/merry-match-project.git`

After this backend is running locally (or deployed), set `VITE_API_BASE_URL` in the frontend `.env` to this API’s base URL (for example `http://localhost:8080`).

## Tech stack

- Java 21, Spring Boot
- Spring Data JPA, PostgreSQL
- Flyway (migrations)
- JWT (`jjwt`, Nimbus)
- Supabase integration (storage and related settings per configuration)

## Prerequisites

- JDK 21
- Maven or the project’s Maven Wrapper (`mvnw` / `mvnw.cmd`)
- A reachable PostgreSQL instance (or a provider-supplied JDBC URL)

## Run locally

```bash
# Windows (PowerShell / CMD)
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Default port is **8080** (`server.port` or env `PORT`).

`application-local.properties` is loaded optionally when present on the classpath and merged with the main configuration.

## Environment variables

| Variable | Purpose |
| -------- | ------- |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing secret for JWTs (must match production settings) |
| `JWT_EXPIRATION` | Token lifetime in milliseconds (default `86400000`) |
| `JWT_AUDIENCE` | If set, validates audience (e.g. Supabase `"authenticated"`) |
| `SUPABASE_URL`, `SUPABASE_API_KEY` | Supabase connectivity |
| `SUPABASE_BUCKET`, `SUPABASE_CHAT_BUCKET` | Storage bucket names for uploads / chat |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins (default `http://localhost:5173`) |
| `OMISE_WEBHOOK_SECRET` | Omise webhook verification (when using Omise) |

Additional options are documented in `src/main/resources/application.properties`.

## Build

```bash
.\mvnw.cmd clean package
```

The runnable JAR is produced under `target/` after a successful build.
