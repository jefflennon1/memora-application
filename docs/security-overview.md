# Security overview

This document describes controls visible in the public code. It is not a production runbook or a guarantee against every class of vulnerability.

## Application controls

- HTTP-only, configurable secure session cookies.
- CSRF protection for state-changing browser requests.
- Explicit CORS origin configuration and redirect allow-listing.
- Local passwords hashed through Spring Security's password encoder.
- OAuth configuration optional and supplied only at runtime.
- Account-status and onboarding guards at both API and UI boundaries.
- Capability-specific authorization for organizers, participants and public visitors.
- Bean Validation plus domain invariants for request and state validation.
- Generic 5xx responses that do not disclose internal exception messages.
- Restricted actuator exposure and health details.

## Media controls

- Configurable request and object-size limits.
- Server-side inspection of image, audio and video structure.
- Generated storage keys instead of user-controlled object paths.
- Private storage access mediated by the application.
- Cleanup paths for replaced and orphaned media.

## Public-repository controls

- Real `.env` files, credentials, deployment workflows and infrastructure identifiers are excluded.
- Checked-in defaults bind only to local services and use conspicuously non-production credentials.
- Production data, logs, backups and private repository history are not included.
- Example problem-type URLs use the reserved `.example` domain.

## Reporting

Please do not publish suspected vulnerabilities in a public issue. Contact the repository owner privately with reproduction details and the affected component.
