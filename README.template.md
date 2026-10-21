# Document Intelligence Master Venue Worker 🏛️

A batch companion worker on the iQ Key Value platform, backed by PostgreSQL, S3, and RabbitMQ.

## Service Role

The service is the **companion worker** (asynchronous sibling service) to the service that owns the control-plane API — not a sidecar. It exposes no inbound HTTP API beyond Actuator health; every trigger arrives as a message. It consumes import batches from the shared S3 bucket, applies them to the master catalog tables, and writes its audit report artefacts back to the bucket. Reads and writes go through MyBatis.

It is expected to share its PostgreSQL instance and RabbitMQ broker with the service that hands it work, rather than owning them outright — a deliberate exception to the platform's self-contained-service rule, traded for simplicity inside a single product boundary. Schema migrations, including the master catalog DDL, stay with that service. What the two share is data, not a host: they run as separate deployables, with independent lifecycles and their own scaling axes.

## About

This service provides:

- **Object storage** — MinIO/S3 client for reading import batches and writing report artefacts
- **Persistence** — data stored in PostgreSQL via MyBatis
- **Messaging** — RabbitMQ connection, Jackson JSON message converter and listener retry policy, gated by `iqkv.messaging.rabbitmq.enabled`

## Quick Links

- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## Tech Stack

- Java 25 / Spring Boot (latest via parent POM)
- MyBatis 3.x (no JPA) + PostgreSQL 17
- MinIO / S3-compatible object storage
- RabbitMQ for async messaging
- Micrometer + Prometheus
- Spring Web — Actuator endpoints only

## Observability

- **Actuator endpoints** — `health` (liveness + readiness probes), `info`, `metrics`, and `prometheus` exposed on the management port (`8081`)
- **Structured logging** — JSON log output via the Logstash encoder
- **Metrics** — no service-specific custom meters yet; the Micrometer/Prometheus registry exposes the standard JVM, HTTP, and Spring Boot meters

## Prerequisites

- JDK 25 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.15.0 & pnpm >= 10.33.2 (git hooks)
- Docker & Docker Compose

## Quick Start

```bash
# Clone the repository
git clone https://github.com/IQKV/docintelligence-master-venue-worker.git
cd docintelligence-master-venue-worker

# Install git hooks
pnpm install

# Copy environment variables
cp .env.example .env.local
# Defaults point to the local compose infrastructure — no edits needed for first-run

# Start infrastructure dependencies (PostgreSQL, RabbitMQ, MinIO)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → Actuator: http://localhost:8081/actuator/health
```

## Environment Variables

| Variable                    | Default                 | Description            |
| :-------------------------- | :---------------------- | :--------------------- |
| `DB_HOST`                   | `localhost`             | PostgreSQL host        |
| `DB_PORT`                   | `5432`                  | PostgreSQL port        |
| `DB_NAME`                   | `catalogservice`        | Database name          |
| `DB_USERNAME`               | `svc_catalog_dba`       | Database user          |
| `DB_PASSWORD`               | `svc_catalog_dba`       | Database password      |
| `RABBITMQ_HOST`             | `localhost`             | RabbitMQ host          |
| `RABBITMQ_PORT`             | `5672`                  | RabbitMQ AMQP port     |
| `RABBITMQ_USERNAME`         | `svc_catalog_rmq`       | RabbitMQ user          |
| `RABBITMQ_PASSWORD`         | `svc_catalog_rmq`       | RabbitMQ password      |
| `OBJECTSTORAGE_ENDPOINT`    | `http://localhost:9000` | S3-compatible endpoint |
| `OBJECTSTORAGE_ACCESS_KEY`  | `iqkv`                  | S3 access key          |
| `OBJECTSTORAGE_SECRET_KEY`  | `iqkv_password`         | S3 secret key          |
| `OBJECTSTORAGE_BUCKET_NAME` | `catalog-assets`        | S3 bucket name         |
| `SPRING_PROFILES_ACTIVE`    | `local`                 | Active Spring profile  |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment.

## Maven Commands

```bash
# Build and test (skip Checkstyle during development)
./mvnw clean verify -Dcheckstyle.skip=true

# Run tests only
./mvnw test -Dcheckstyle.skip=true

# Explicit Checkstyle check
./mvnw checkstyle:check

# Coverage report → target/site/jacoco/index.html
./mvnw jacoco:report

# Production build
./mvnw clean package -Pproduction
```

## Docker

```bash
# Build image
docker build -t iqkv/docintelligence-master-venue-worker:latest .

# Run with full platform stack
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                      |
| :------------------------- | :------------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes      |
| `GET /actuator/info`       | Build info and platform metadata |
| `GET /actuator/metrics`    | Application metrics              |
| `GET /actuator/prometheus` | Prometheus scrape endpoint       |

## Project Structure

```
src/main/java/com/iqkv/docintelligence/mastervenueworker/
├── infrastructure/   # Technical concerns: Spring config, RabbitMQ, MyBatis, object storage
├── mastervenue/      # Master catalog model and MyBatis mapper
├── shared/           # Common utilities, value objects
└── MasterVenueWorkerApplication.java
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Object storage**: MinIO/S3 client configured via `iqkv.objectstorage.*` (`OBJECTSTORAGE_ENDPOINT`, `OBJECTSTORAGE_ACCESS_KEY`, `OBJECTSTORAGE_SECRET_KEY`, `OBJECTSTORAGE_BUCKET_NAME`)
- **Persistence**: MyBatis with XML mappers + PostgreSQL
- **Messaging**: RabbitMQ connection, Jackson JSON message converter and listener retry policy; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base profile, enabled per environment
- **Web**: Spring Web is present only to serve Actuator HTTP endpoints (management port 8081)
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes readiness/liveness
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
