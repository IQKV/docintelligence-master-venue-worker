## 📜 Deployment Guide

### Overview

The Document Intelligence Master Venue Worker is deployed using Helm charts and automated CI/CD pipelines. The service connects to PostgreSQL, RabbitMQ, and S3-compatible object storage.

### Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- External infrastructure services (PostgreSQL, RabbitMQ, S3-compatible object storage)

### Environments

| Environment | Namespace      | Purpose                   |
| ----------- | -------------- | ------------------------- |
| Test        | `iqkv-sit-env` | Feature branch testing    |
| Staging     | `iqkv-uat-env` | Pre-production validation |
| Production  | `iqkv-prd-env` | Live production           |

### Automated Deployment (CI/CD)

#### Drone Pipeline Overview

<details>
<summary>📋 Pipeline Stages</summary>

The service uses a Drone CI/CD pipeline with 10 stages:

1. **VerifyCode** — Code quality, tests, static analysis
2. **PublishArtifacts** — Maven artifacts to Nexus
3. **PublishDockerImage** — Container images to registry
4. **DeployWorkInProgress** — WIP branch auto-deployment to SIT
5. **RollbackWorkInProgress** — WIP rollback
6. **PromoteFeatureDeployment** — Feature branch promotion to SIT
7. **RollbackFeatureDeployment** — Feature rollback
8. **PromoteDeployment** — Release promotion to UAT/PRD
9. **RollbackDeployment** — Release rollback
10. **ReleasePackage** — Automated version management

</details>

<details>
<summary>🔐 Required Drone Secrets</summary>

| Secret Name                       | Purpose                           | Used In                                    |
| --------------------------------- | --------------------------------- | ------------------------------------------ |
| `NEXUS_DEPLOYER_USERNAME`         | Nexus repository authentication   | Artifact publishing, dependency resolution |
| `NEXUS_DEPLOYER_PASSWORD`         | Nexus repository authentication   | Artifact publishing, dependency resolution |
| `SONAR_HOST`                      | SonarQube server URL              | Static code analysis                       |
| `SONAR_TOKEN`                     | SonarQube authentication token    | Static code analysis                       |
| `SLACK_WEBHOOK`                   | Slack notifications webhook URL   | Build status notifications                 |
| `GITHUB_API_ACCESS_TOKEN`         | GitHub API access for releases    | Release creation, changelog generation     |
| `SVC_CONTAINER_REGISTRY_USERNAME` | Container registry authentication | Docker image publishing                    |
| `SVC_CONTAINER_REGISTRY_PASSWORD` | Container registry authentication | Docker image publishing                    |
| `HELM_CHARTS_REPOSITORY`          | Helm charts repository URL        | Kubernetes deployments                     |
| `INFRA_POSTGRESQL_PASSWORD`       | PostgreSQL database password      | Application configuration                  |
| `INFRA_RABBITMQ_PASSWORD`         | RabbitMQ message broker password  | Application configuration                  |

> No `SMTP_*` secrets required — the Master Venue Worker does not send email.

</details>

#### Branch Deployment Strategy

| Branch Type | Auto Deploy | Manual Promote | Target Environment |
| ----------- | ----------- | -------------- | ------------------ |
| `wip`       | ✅ SIT      | -              | SIT                |
| `feature/*` | -           | ✅ SIT         | SIT                |
| Tags        | -           | ✅ UAT / PRD   | UAT / Production   |

#### Deployment Commands

<details>
<summary>Helm Commands</summary>

```bash
# WIP / Feature branches (SIT)
helm upgrade --install --atomic --wait --timeout 5m docintelligence-master-venue-worker ./ \
  --values ./values.yaml \
  --values ./values-sit.yaml \
  --set image.tag=${DRONE_BRANCH} \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --namespace iqkv-sit-env \
  --create-namespace

# Production (tagged releases)
helm upgrade --install --atomic --wait --timeout 5m docintelligence-master-venue-worker ./ \
  --values ./values.yaml \
  --values ./values-prd.yaml \
  --set image.tag=${DRONE_TAG} \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --namespace iqkv-prd-env \
  --create-namespace
```

</details>

### Manual Deployment

#### Quick Start

```bash
# Clone Helm charts
git clone <HELM_CHARTS_REPOSITORY> charts
cd charts/IQKV/docintelligence-master-venue-worker

# Deploy to SIT
helm upgrade --install docintelligence-master-venue-worker ./ \
  --values values-sit.yaml \
  --set infraServices.postgresql.password="your-db-password" \
  --set infraServices.rabbitmq.password="your-rabbitmq-password" \
  --namespace iqkv-sit-env \
  --create-namespace
```

#### Secret Configuration Examples

```bash
drone secret add --repository IQKV/docintelligence-master-venue-worker --name INFRA_POSTGRESQL_PASSWORD --data "your-postgresql-password"
drone secret add --repository IQKV/docintelligence-master-venue-worker --name INFRA_RABBITMQ_PASSWORD --data "your-rabbitmq-password"
```

#### External Services

The service connects to these external infrastructure components:

- **PostgreSQL**: Master catalog persistence (shared instance; migrations are owned by `docintelligence-catalog-service`)
- **RabbitMQ**: `admin.master-catalog.import.*` import events
- **S3-compatible object storage**: master catalog import batches and report artefacts

#### Service Configuration

| Setting        | SIT      | UAT      | Production    |
| -------------- | -------- | -------- | ------------- |
| Replicas       | 1        | 1        | 2             |
| CPU Request    | 300m     | 500m     | 500m          |
| CPU Limit      | 750m     | 1000m    | 1000m         |
| Memory Request | 384Mi    | 512Mi    | 512Mi         |
| Memory Limit   | 768Mi    | 1Gi      | 1Gi           |
| Autoscaling    | Disabled | Disabled | 2–10 replicas |
| Ingress        | Disabled | Disabled | Configurable  |
| Monitoring     | Disabled | Enabled  | Enabled       |
| Network Policy | Disabled | Disabled | Enabled       |

### Monitoring & Health Checks

#### Health Endpoints

- **Liveness**: `/actuator/health/liveness` (port 8081)
- **Readiness**: `/actuator/health/readiness` (port 8081)
- **Metrics**: `/actuator/prometheus` (port 8081)

#### Monitoring Stack

UAT and production deployments include:

- Prometheus ServiceMonitor
- Alerting rules: service down, high memory (>85%), high CPU (>80%), high error rate (>5% 5xx), slow responses (p95 >10s)
- Grafana dashboards

### Troubleshooting

#### Common Issues

1. **Database connection failures**

    ```bash
    kubectl logs deployment/docintelligence-master-venue-worker -n iqkv-sit-env
    ```

2. **Check configuration**

    ```bash
    kubectl describe configmap docintelligence-master-venue-worker-config -n iqkv-sit-env
    kubectl describe secret docintelligence-master-venue-worker-secrets -n iqkv-sit-env
    ```

3. **Test health endpoints**

    ```bash
    kubectl port-forward deployment/docintelligence-master-venue-worker 8081:8081 -n iqkv-sit-env
    curl http://localhost:8081/actuator/health
    ```

#### Missing Secrets Diagnosis

```bash
kubectl get secrets -n iqkv-sit-env
kubectl get secret docintelligence-master-venue-worker-secrets -n iqkv-sit-env -o yaml
drone secret ls --repository IQKV/docintelligence-master-venue-worker
```

#### Rollback

```bash
helm rollback docintelligence-master-venue-worker -n iqkv-prd-env
# Or uninstall completely
helm uninstall docintelligence-master-venue-worker -n iqkv-prd-env
```

### Security

- All sensitive values injected via `--set` flags from Drone secrets (never stored in chart)
- TLS configurable via cert-manager in production ingress
- Network policies restrict pod communication in production — includes explicit egress rules for PostgreSQL, RabbitMQ, and S3-compatible object storage
- Non-root container execution (UID 1001)
- Read-only root filesystem in production
