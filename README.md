# Memora

> Moments pass. Memories remain.

Memora is a social platform for collecting the photos, videos, audio and stories that people create around shared events. Organizers can manage participation and invitations; guests can contribute memories and continue the conversation through comments, mentions and reactions.

[Português (Brasil)](README.pt-BR.md) · [Live product](https://memora-social.com.br/login)

> The live link is provided only as a product demonstration. This repository's development configuration never points to the production API, database or object storage.

## What this repository shows

- Local and Google sign-in, session security, CSRF protection and onboarding.
- Public, private and invitation-based events with participant roles.
- Invitations, join requests, attendance and organizer controls.
- Memories with image, audio and video processing.
- Comments, mentions, emoji reactions and engagement summaries.
- Structured Problem Details errors and responsive React interfaces.
- Versioned PostgreSQL migrations and integration tests with Testcontainers.

## Architecture

Memora is a modular monolith: a Java 21 / Spring Boot 3 API and a React 19 / TypeScript single-page application. PostgreSQL stores transactional data and S3-compatible object storage keeps media. The frontend uses React Router and TanStack Query.

```text
Browser (React) -> Spring Boot API -> PostgreSQL
                         |
                         +---------> S3-compatible storage
```

See [Architecture](docs/architecture.md), [Domain model](docs/domain-model.md) and [Technical decisions](docs/technical-decisions.md) for the deeper design.

## Run locally

Prerequisites: Java 21, Maven 3.9+, Node.js 22+, npm and Docker.

```bash
docker compose up -d postgres minio

mvn -f backend/pom.xml spring-boot:run

npm --prefix frontend ci
npm --prefix frontend run dev
```

The defaults use only local endpoints: web at `http://localhost:3000`, API at `http://localhost:8080`, PostgreSQL at `127.0.0.1:5434` and MinIO at `127.0.0.1:9000`. Create the `memora-local` bucket before testing uploads. OAuth remains disabled unless local credentials are supplied.

Complete setup and validation commands are in [Local development](docs/local-development.md).

## Validate

```bash
mvn -B -f backend/pom.xml verify
npm --prefix frontend test -- --run
npm --prefix frontend run build
```

The current showcase contains 102 backend unit tests, 125 backend integration tests and 107 frontend tests.

## Security and public scope

This is a curated, sanitized snapshot assembled from private development repositories. It intentionally excludes production credentials, deployment automation, infrastructure identifiers, user data, logs, backups and private Git history. Checked-in credentials are conspicuous local-only placeholders.

Read [Security overview](docs/security-overview.md) before adapting the project. Please report suspected vulnerabilities privately to the repository owner rather than opening a public issue.

## Repository layout

```text
backend/      Spring Boot API, migrations and tests
frontend/     React application and tests
docs/         Architecture, domain and security notes
compose.yaml  Local PostgreSQL and MinIO services
```

## License

No open-source license is granted. All rights are reserved unless a license is added explicitly.
