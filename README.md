# Document Intelligence Master Venue Worker 🏛️

Master venue catalog loader on the iQ Key Value platform. A deterministic, LLM-free batch worker backed by PostgreSQL, S3, and RabbitMQ.

## Service Role

This is the batch half of the master catalog pipeline — the **companion worker** (asynchronous sibling service) to `docintelligence-catalog-service`, not a sidecar. It exposes no inbound HTTP API beyond Actuator health — every trigger arrives as a message.

1. **Dry-run** — on `admin.master-catalog.import.dry-run`, read the scraper's import batch from S3 and emit a CSV audit report. Every row carries the recommended action (`INSERT` / `MERGE #id` / `SKIP`) together with name similarity, geo distance, and the candidate list.
2. **Apply** — once an admin has reviewed and re-uploaded the annotated CSV, `admin.master-catalog.import.apply` applies exactly the reviewed actions. Nothing is auto-applied; the worker makes no autonomous deduplication decisions.
3. **Write** — rows land in `public.master_venue`, `public.master_venue_alias`, and `public.master_venue_external`. Deduplication and alias normalisation are deterministic (`pg_trgm` similarity plus PostGIS distance); no LLM is involved anywhere in the path.

The worker never reads or writes a tenant schema, and it never runs migrations.

### Shared Infrastructure

The worker deliberately shares the PostgreSQL instance and the RabbitMQ broker with the rest of the product, and reads its input from the same S3 bucket the scrapers write to. This is a conscious exception to the platform's self-contained-service rule, traded for simplicity inside a single product boundary.

DDL for the master catalog tables stays with `docintelligence-catalog-service` (its `system/master.xml` changelog); this worker is only the write owner of those rows. What the two share is data, not a host: they run as separate deployables with independent lifecycles — the catalog service scales on API traffic, this worker on import batches. That is what makes it a **companion service** rather than a sidecar.

## About

This service provides:

- **Maven single-module layout** — standard `src/main/java` structure with MyBatis, PostgreSQL, RabbitMQ, and MinIO wired up
- **Multi-profile Spring config** — `local`, `sit`, `uat`, `prd` profiles with correct defaults per environment
- **Docker Compose** — `compose.base.yaml` with PostgreSQL, RabbitMQ, MinIO, DbGate, Prometheus, and Grafana; `compose.container.yaml` for the full runtime stack including the service container
- **Dockerfile** — multi-stage build with layered JAR extraction, non-root `appuser`, and JVM tuning
- **Database Initialization** — `docker/postgres/init-catalog.sql` for automated schema and extension setup
- **Messaging** — RabbitMQ connection, Jackson JSON message converter and listener retry policy, gated by `iqkv.messaging.rabbitmq.enabled`
- **Object storage** — MinIO/S3 client for reading import batches and writing report artefacts
- **Observability** — Micrometer + Prometheus + structured JSON logging (Logstash encoder); Actuator HTTP endpoints (the only use of Spring Web)
- **Quality tools** — Checkstyle, JaCoCo, ArchUnit, Husky git hooks, commitlint

## Quick Links

- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## Tech Stack

- Java 25 / Spring Boot (latest via parent POM)
- MyBatis 3.x (no JPA) + PostgreSQL 17 (pg_trgm, PostGIS)
- `com.iqkv:docintelligence-venue` — shared venue model and mapper
- MinIO / S3-compatible object storage
- RabbitMQ for async messaging
- Micrometer + Prometheus
- Spring Web — Actuator endpoints only

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
# Defaults point to the local compose infrastructure — no edits needed for first run

# Start dependencies (PostgreSQL, RabbitMQ, MinIO)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Pdev
# → Actuator: http://localhost:8081/actuator/health
```

## Environment Variables

| Variable                         | Default                  | Description                             |
| -------------------------------- | ------------------------ | --------------------------------------- |
| `DB_HOST`                        | `localhost`              | PostgreSQL host                         |
| `DB_PORT`                        | `5432`                   | PostgreSQL port                         |
| `DB_NAME`                        | `catalogservice`         | Database name                           |
| `DB_USERNAME`                    | `svc_catalog_dba`        | Database user                           |
| `DB_PASSWORD`                    | `svc_catalog_dba`        | Database password                       |
| `RABBITMQ_HOST`                  | `localhost`              | RabbitMQ host                           |
| `RABBITMQ_PORT`                  | `5672`                   | RabbitMQ AMQP port                      |
| `RABBITMQ_USERNAME`              | `svc_catalog_rmq`        | RabbitMQ user                           |
| `RABBITMQ_PASSWORD`              | `svc_catalog_rmq`        | RabbitMQ password                       |
| `OBJECTSTORAGE_ENDPOINT`         | `http://localhost:9000`  | S3-compatible endpoint                  |
| `OBJECTSTORAGE_ACCESS_KEY`       | `iqkv`                   | S3 access key                           |
| `OBJECTSTORAGE_SECRET_KEY`       | `iqkv_password`          | S3 secret key                           |
| `OBJECTSTORAGE_BUCKET_NAME`      | `catalog-assets`         | Bucket holding the master catalog paths |
| `INGEST_SOURCE_TAGVENUE_ENABLED` | `true`                   | Enable the tagvenue data provider       |
| `INGEST_DIR`                     | `/data/mc-ingest`        | mc-ingest tree written by the scraper   |
| `INGEST_POLL_INTERVAL`           | `PT30S`                  | Delay between ingest tree scans         |
| `MESSAGING_ENABLED`              | `false`                  | RabbitMQ listener toggle                |
| `SPRING_PROFILES_ACTIVE`         | `local`                  | Active Spring profile                   |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment. Import batches live under the `docintelligence/master-catalog/imports/{importId}/` prefix of the shared bucket.

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

# Run full stack (service + dependencies)
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                   | Description                 |
| -------------------------- | --------------------------- |
| `GET /actuator/health`     | Liveness + readiness probes |
| `GET /actuator/metrics`    | Application metrics         |
| `GET /actuator/prometheus` | Prometheus scrape endpoint  |

## Project Structure

```
.
├── docker/
│   ├── postgres/       # DB initialization scripts (init-catalog.sql)
│   ├── prometheus/     # Prometheus configuration
│   └── grafana/        # Grafana dashboards and datasources
├── src/main/java/com/iqkv/docintelligence/mastervenueworker/
│   ├── infrastructure/ # Spring config, MyBatis, RabbitMQ, object storage
│   └── shared/         # Common utilities, value objects
```

## License

This project is licensed under the Apache License. See the [LICENSE](LICENSE) file for details.

## Branch Strategy

This repository uses **scaled trunk-based development**:

- `dev` is the trunk — always stable and deployable
- Short-lived feature/bugfix branches merge into `dev` via pull request
- Releases are cut from `dev` as annotated tags (`v1.2.0`) — no separate release branch

```
dev  ← trunk
├── feature/*
├── bugfix/*
├── improvement/*
├── hotfix/*
└── rfc/*
```

## Contributing

Please read our [Contributing Guidelines](.github/CONTRIBUTING.md) and [Code of Conduct](.github/CODE_OF_CONDUCT.md).

---

## 🧩 Architecture

- **Persistence**: MyBatis with XML mappers + PostgreSQL; writes `public.master_venue*` only, never a tenant schema
- **Import pipeline**: `admin.master-catalog.import.dry-run` / `admin.master-catalog.import.apply` events; nothing is applied without a reviewed batch
- **Messaging**: RabbitMQ connection, Jackson JSON message converter and listener retry policy; `iqkv.messaging.rabbitmq.enabled` toggle — disabled in base, enabled per profile
- **Object storage**: MinIO/S3 client reading `docintelligence/master-catalog/imports/{importId}/`
- **Web**: Spring Web is present only to serve Actuator HTTP endpoints (management port 8081)
- **Credential convention**: DB users follow `svc_{service}_dba`, RabbitMQ users follow `svc_{service}_rmq`
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes
- **GitHub Integration**: Issue templates, labels, Dependabot, and CI workflows
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
