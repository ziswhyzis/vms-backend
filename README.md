# Stark Group VMS — Backend API

The REST API and SQL layer for the Stark Group Vehicle Management System. It
owns authentication, business rules and persistence; the Flutter Web client in
the separate `vms-frontend` repository only talks to it over HTTP.

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Security, Validation) |
| Database | H2 file database locally, PostgreSQL in production |
| Auth | Stateless JWT (HS256) with BCrypt-hashed passwords |
| Tests | JUnit 5 + MockMvc + Spring Data tests (55) |
| Deploy | Render (Docker) + Render PostgreSQL |

## API

Base URL `http://localhost:8080`. Every endpoint except `POST /auth/login`,
`POST /auth/register` and `GET /health` requires `Authorization: Bearer <JWT>`.

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | Returns a JWT and the operator profile |
| POST | `/auth/register` | Creates an operator account and returns a JWT (`201`) |
| GET | `/auth/me` | Current operator |
| GET | `/vehicles?page=0&size=10&sort=vehicleNumber,asc` | Paginated register |
| GET | `/vehicles/{id}` | One vehicle |
| POST | `/vehicles` | `201`, `409` duplicate number, `400` validation |
| PUT | `/vehicles/{id}` | `200`, `409`, `400`, `404` |
| DELETE | `/vehicles/{id}` | `204`, `404` |
| GET | `/health` | Public liveness probe |

Vehicle fields: **vehicle number, company, type, brand, model, status**.

A page response always has exactly these keys:

```json
{
  "content": [
    {
      "id": 1,
      "vehicleNumber": "DHA-GA-11-1001",
      "company": "Stark Group",
      "type": "SUV",
      "brand": "Toyota",
      "model": "Corolla Cross",
      "status": "ACTIVE",
      "createdAt": "2026-11-17T05:20:11Z",
      "updatedAt": "2026-11-17T05:20:11Z"
    }
  ],
  "number": 0,
  "size": 10,
  "totalElements": 24,
  "totalPages": 3,
  "last": false
}
```

Every error uses one envelope, and `message` is always present:

```json
{
  "timestamp": "2026-11-17T05:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Vehicle number 'DHA-GA-11-1001' already exists.",
  "path": "/vehicles",
  "fieldErrors": { "vehicleNumber": "Vehicle number already exists." }
}
```

Full request/response examples: [`docs/API.md`](docs/API.md).

### Rules enforced in the service layer

- Vehicle numbers are trimmed, whitespace-collapsed and upper-cased.
- A duplicate number is rejected with `409` before saving; the SQL unique index
  `ux_vehicles_vehicle_number` is the final guarantee against races.
- Validation: number `[A-Za-z0-9-]` 4–20 chars, company 2–60, brand/model 2–40,
  `type` one of `CAR, SUV, PICKUP, VAN, TRUCK, BUS`, `status` one of
  `ACTIVE, INACTIVE, MAINTENANCE` (defaults to `ACTIVE`).
- `page` is clamped to `>= 0`, `size` to `1..100`, and `sort` is checked against
  an allow-list.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (Render injects this) |
| `APP_JWT_SECRET` | dev secret | HMAC key, **≥ 32 bytes** — required in the `prod` profile |
| `APP_JWT_EXPIRATION_MINUTES` | `480` | Token lifetime |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:[*],http://127.0.0.1:[*]` | Exact origins or patterns (`http://localhost:[*]`) |
| `APP_SEED_DATA` | `true` | Seed the operator and demo fleet when missing |
| `APP_SEED_ADMIN_USERNAME` | `admin` | Bootstrap operator username |
| `APP_SEED_ADMIN_PASSWORD` | `admin123` (local only) | Bootstrap operator password |
| `APP_SEED_ADMIN_NAME` | `Stark Group Administrator` | Display name |
| `SPRING_PROFILES_ACTIVE` | — | `prod` for PostgreSQL |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | — | PostgreSQL connection (prod profile) |

No credential is committed to this repository. Locally the defaults above are
used; in the `prod` profile the JWT secret and the bootstrap password are
mandatory environment variables, and the application refuses to start without
them rather than falling back to a development value.
