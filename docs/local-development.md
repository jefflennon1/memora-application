# Local development

## Prerequisites

- Java 21 and Maven 3.9+
- Node.js 22+ and npm
- Docker Desktop or a compatible Docker engine

All documented endpoints are local. Do not substitute production services or credentials.

## Start dependencies

```bash
docker compose up -d postgres minio
```

PostgreSQL is exposed at `127.0.0.1:5434`; MinIO uses `127.0.0.1:9000` with its local console at `127.0.0.1:9001`. The values in `compose.yaml` are development-only.

Create the object-storage bucket `memora-local` in MinIO before testing uploads.

## Run the API

The committed defaults already match the Compose services. Copy `backend/.env.example` to `backend/.env` only when you need local overrides; `.env` is ignored.

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`; OpenAPI UI is available at `http://localhost:8080/docs`.

## Run the web application

```bash
cd frontend
npm ci
npm run dev
```

The web application listens on `http://localhost:3000` and calls only `http://localhost:8080` by default.

## Validate

```bash
mvn -B -f backend/pom.xml verify
npm --prefix frontend test -- --run
npm --prefix frontend run build
```

Backend integration tests use disposable PostgreSQL containers and do not use the Compose database. OAuth login remains disabled unless local test credentials are explicitly supplied; local username/password flows remain available.
