# BizLink Kubernetes Deployment

## Files

| File | Purpose |
|------|---------|
| `namespace.yaml` | `bizlink` namespace |
| `configmap.yaml` | Non-sensitive app config (DB URL, ports) |
| `secret.yaml` | DB password, JWT secret, Razorpay keys |
| `deployment.yaml` | Spring Boot API deployment |
| `service.yaml` | ClusterIP service (port 80 → 8080) |
| `ingress.yaml` | NGINX ingress |

## Prerequisites

- Kubernetes cluster with NGINX Ingress Controller
- PostgreSQL database reachable at `postgres:5432` (or update `configmap.yaml` `DB_URL`)
- Docker image built and pushed to your registry

## Build Docker Image

```bash
cd bizlink
docker build -t bizlink-api:latest .
# docker tag bizlink-api:latest your-registry/bizlink-api:latest
# docker push your-registry/bizlink-api:latest
```

Update `k8s/deployment.yaml` image if using a private registry.

## Configure Secrets

Edit `k8s/secret.yaml` before production deploy:

```yaml
stringData:
  DB_PASSWORD: "your-db-password"
  JWT_SECRET: "your-256-bit-secret"
  RAZORPAY_KEY_ID: "rzp_..."
  RAZORPAY_KEY_SECRET: "..."
  RAZORPAY_WEBHOOK_SECRET: "..."
```

## Deploy

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
```

Or apply all at once:

```bash
kubectl apply -f k8s/
```

## Verify

```bash
kubectl get pods -n bizlink
kubectl logs -f deployment/bizlink-api -n bizlink
```

API: `http://api.bizlink.local` (add to hosts file or change ingress host)

## Super Admin (auto-created on startup)

| Email | Password |
|-------|----------|
| `anji@gmail.com` | `Anji@ashi@201` |

Liquibase migrations `012` and `013` create/update this user automatically.

## Notes

- Uploads use `emptyDir` volume — for production use PersistentVolumeClaim
- Ensure PostgreSQL `bizlink` database exists before first deploy
- Liquibase runs migrations automatically on pod start
