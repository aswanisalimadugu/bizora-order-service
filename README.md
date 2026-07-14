# BizLink Backend

Digital Business Card + Mini Website + WhatsApp CRM SaaS platform.

## Tech Stack

- Java 21
- Spring Boot 3.3
- Spring Security + JWT
- Spring Data JPA + Hibernate
- PostgreSQL
- Liquibase (database migrations)
- Razorpay (payments)
- Swagger OpenAPI

## Project Location

```
C:\Users\anjir\Desktop\personal\Anji\dream project\bizlink
```

## Prerequisites

1. **Java 21** installed
2. **Maven** installed
3. **PostgreSQL** running locally

## Database Setup

PostgreSQL lo `bizlink` database create cheyandi (one-time only):

```sql
CREATE DATABASE bizlink;
```

> Tables and schema **Liquibase** dwara app start avvagane automatic ga create avuthayi. Manual table creation avasaram ledu.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bizlink` | Database URL |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `postgres` | DB password |
| `JWT_SECRET` | dev secret | Min 256-bit secret for production |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiry (24h) |
| `UPLOAD_DIR` | `uploads` | Local image storage |
| `RAZORPAY_KEY_ID` | — | Razorpay key (optional for dev) |
| `RAZORPAY_KEY_SECRET` | — | Razorpay secret |
| `RAZORPAY_WEBHOOK_SECRET` | — | Webhook signature secret |

## Run Backend

```bash
cd "C:\Users\anjir\Desktop\personal\Anji\dream project\bizlink"
mvn spring-boot:run
```

Server: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

## API Endpoints

### Auth (Public)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register owner |
| POST | `/api/auth/login` | Login |

### Business (Auth required)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/business` | Create business (multipart) |
| GET | `/api/business/{id}` | Get business |
| GET | `/api/business/my` | My businesses |
| PUT | `/api/business/{id}` | Update business |
| DELETE | `/api/business/{id}` | Delete business |

### Public
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/public/business/{slug}` | Public business page |

### Categories, Products, Customers, Orders, Subscription, Payment
See Swagger UI for full documentation.

## Subscription Plans (Seeded)

| Plan | Price | Duration |
|------|-------|----------|
| Starter | ₹499 | 30 days |
| Growth | ₹999 | 30 days |
| Pro | ₹1,999 | 30 days |

## Image Uploads

Images stored locally in `uploads/` folder:
- `uploads/logos/`
- `uploads/covers/`
- `uploads/products/`

Served at: `http://localhost:8080/uploads/...`

Later AWS S3 ki migrate cheyachu — `BusinessService` and `ProductService` lo `saveFile` method replace cheyandi.

## Super Admin (Auto-created)

Liquibase automatically creates/updates on app startup:

| Email | Password | Role |
|-------|----------|------|
| `anji@gmail.com` | `Anji@ashi@201` | ADMIN |

Migrations: `012-add-admin-fields.yaml`, `013-ensure-anji-super-admin.yaml`

## Kubernetes Deployment

See [`k8s/README.md`](k8s/README.md) for full instructions.

```bash
docker build -t bizlink-api:latest .
kubectl apply -f k8s/
```

K8s folder contains: `namespace`, `configmap`, `secret`, `deployment`, `service`, `ingress`.
