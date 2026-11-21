# Technical decisions

## Modular monolith

A single deployable keeps transactions and operations straightforward while capability-oriented packages preserve boundaries that can evolve independently. This is a deliberate fit for the product's current scale, not an assumption that every module must remain in one process forever.

## PostgreSQL and Flyway

PostgreSQL provides relational constraints, search-friendly data types and transactional consistency. Versioned Flyway migrations make schema changes reviewable and reproducible; Hibernate uses `validate` rather than creating production schemas.

## Session authentication

The browser uses an HTTP-only session cookie. State-changing requests also require a CSRF token. This avoids exposing long-lived bearer tokens to browser JavaScript and supports both local credentials and optional OAuth login.

## Problem Details

API failures use RFC 9457-style Problem Details with stable application codes. The frontend maps those codes to contextual messages without depending on internal exception text.

## Object storage abstraction

Media bytes live in S3-compatible object storage while relational metadata remains in PostgreSQL. Upload validation inspects content and dimensions/duration instead of trusting filenames alone. MinIO provides a local implementation without coupling development to a cloud account.

## React and TanStack Query

React Router owns navigation and guards; TanStack Query owns remote cache, invalidation and mutations. Feature folders keep UI, models and behavior close to the capability they represent.

## Tests as architecture checks

Fast unit tests cover value objects and policies. Integration tests run the real Spring context, migrations and PostgreSQL through Testcontainers. Frontend tests execute routed user flows with a mocked HTTP boundary rather than replacing feature components.
