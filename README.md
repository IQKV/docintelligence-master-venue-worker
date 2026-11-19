# Document Intelligence Master Venue Worker 🏛️

Master venue catalog loader on the iQ Key Value platform. A scheduled ingest worker — it loads scraped venue records into the master catalog and generates their embeddings — backed by PostgreSQL, S3, and RabbitMQ.

## Service Role

This is the batch half of the master catalog pipeline — the **companion worker** (asynchronous sibling service) to `docintelligence-catalog-service`, not a sidecar. It exposes no inbound HTTP API beyond Actuator health; every cycle is driven by a scheduled poller, not an inbound request.

1. **Poll** — on a fixed delay (`iqkv.ingest.poll-interval`, default 30 minutes) the scheduler scans the ingest tree at `iqkv.ingest.dir` — a volume shared with the scraper, holding one record folder per venue (`venue.json` plus an `assets/` folder).
2. **Load** — each ready record is upserted into the master catalog: the venue row, its external identity, and its assets, uploaded to MinIO. The scraped folder is removed once everything is written; a record that fails stays on disk and is retried on the next cycle.
3. **Embed** — each ingested venue is embedded at ingest time via Ollama (`nomic-embed-text`) and the vector persisted through `MasterVenueMapper#updateEmbedding`. A backfill pass re-embeds any rows whose `embedding` column is still `NULL`.

Providers are enabled per source via `iqkv.ingest.source.<key>.enabled` (e.g. `tagvenue`). Embedding failures are logged and swallowed — the venue stays searchable by keyword and is picked up by the next backfill.

The worker never reads or writes a tenant schema, and it never runs migrations.

### Shared Infrastructure

The worker deliberately shares the PostgreSQL instance and the RabbitMQ broker with the rest of the product, and reads its input from the same S3 bucket the scrapers write to. This is a conscious exception to the platform's self-contained-service rule, traded for simplicity inside a single product boundary.

DDL for the master catalog tables stays with `docintelligence-catalog-service` (its `system/master.xml` changelog); this worker is only the write owner of those rows. What the two share is data, not a host: they run as separate deployables with independent lifecycles — the catalog service scales on API traffic, this worker on ingest cycles. That is what makes it a **companion service** rather than a sidecar.

## About

This service provides:

- **Maven single-module layout** — standard `src/main/java` structure with MyBatis, PostgreSQL, RabbitMQ, and MinIO wired up
- **Multi-profile Spring config** — `local`, `sit`, `uat`, `prd` profiles with correct defaults per environment
- **Docker Compose** — `compose.base.yaml` with PostgreSQL, RabbitMQ, MinIO, DbGate, Prometheus, and Grafana; `compose.container.yaml` for the full runtime stack including the service container
- **Dockerfile** — multi-stage build with layered JAR extraction, non-root `appuser`, and JVM tuning
- **Database Initialization** — `docker/postgres/init-catalog.sql` for automated schema and extension setup
- **Messaging** — RabbitMQ connection, Jackson JSON message converter and listener retry policy, gated by `iqkv.messaging.rabbitmq.enabled` (config present, no consumers wired yet)
- **Object storage** — MinIO/S3 client (`catalog-assets` bucket) for writing the assets of each ingested venue
- **Embeddings** — Spring AI Ollama client; venues are embedded with `nomic-embed-text` at ingest time, with a backfill pass for `NULL` rows
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
- Spring AI Ollama starter — `nomic-embed-text` embeddings
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

| Variable                         | Default                 | Description                             |
| -------------------------------- | ----------------------- | --------------------------------------- |
| `DB_HOST`                        | `localhost`             | PostgreSQL host                         |
| `DB_PORT`                        | `5432`                  | PostgreSQL port                         |
| `DB_NAME`                        | `catalogservice`        | Database name                           |
| `DB_USERNAME`                    | `svc_catalog_dba`       | Database user                           |
| `DB_PASSWORD`                    | `svc_catalog_dba`       | Database password                       |
| `RABBITMQ_HOST`                  | `localhost`             | RabbitMQ host                           |
| `RABBITMQ_PORT`                  | `5672`                  | RabbitMQ AMQP port                      |
| `RABBITMQ_USERNAME`              | `svc_catalog_rmq`       | RabbitMQ user                           |
| `RABBITMQ_PASSWORD`              | `svc_catalog_rmq`       | RabbitMQ password                       |
| `OBJECTSTORAGE_ENDPOINT`         | `http://localhost:9000` | S3-compatible endpoint                  |
| `OBJECTSTORAGE_ACCESS_KEY`       | `iqkv`                  | S3 access key                           |
| `OBJECTSTORAGE_SECRET_KEY`       | `iqkv_password`         | S3 secret key                           |
| `OBJECTSTORAGE_BUCKET_NAME`      | `catalog-assets`        | Bucket holding the master catalog paths |
| `INGEST_SOURCE_TAGVENUE_ENABLED` | `true`                  | Enable the tagvenue data provider       |
| `INGEST_DIR`                     | `/data/mc-ingest`       | mc-ingest tree written by the scraper   |
| `INGEST_POLL_INTERVAL`           | `PT30M`                 | Delay between ingest tree scans         |
| `MESSAGING_ENABLED`              | `false`                 | RabbitMQ listener toggle                |
| `SPRING_PROFILES_ACTIVE`         | `local`                 | Active Spring profile                   |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values per environment. The ingest tree (`INGEST_DIR`) is a volume shared with the scraper — one record folder per venue, each holding a `venue.json` and an `assets/` folder. Assets are copied into the `catalog-assets` bucket on load.

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
│   ├── ingest/         # Ingest slice: poller, loader, embedding, venue providers/storage
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
- **Ingest pipeline**: a scheduled poller (`iqkv.ingest.poll-interval`) scans `iqkv.ingest.dir`, loads each record folder (`venue.json` + `assets/`) into the master catalog, and embeds it via Ollama — providers are toggled per source with `iqkv.ingest.source.<key>.enabled`
- **Embeddings**: Spring AI Ollama (`nomic-embed-text`); each ingested venue is embedded and stored through `MasterVenueMapper#updateEmbedding`, with a backfill pass for `NULL` rows
- **Messaging**: RabbitMQ connection, Jackson JSON message converter and listener retry policy; `iqkv.messaging.rabbitmq.enabled` toggle — config present but no consumers bound yet
- **Object storage**: MinIO/S3 client writing each venue's assets to the `catalog-assets` bucket
- **Web**: Spring Web is present only to serve Actuator HTTP endpoints (management port 8081)
- **Credential convention**: DB users follow `svc_{service}_dba`, RabbitMQ users follow `svc_{service}_rmq`
- **Observability**: Micrometer + Prometheus; structured JSON logging with Logstash encoder; health probes for Kubernetes
- **GitHub Integration**: Issue templates, labels, Dependabot, and CI workflows
- **Quality Tools**: Checkstyle, JaCoCo (60% gate), ArchUnit, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for repository structure, DDD patterns, and agent guidelines.
