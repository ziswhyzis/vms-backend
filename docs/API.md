# Stark Group VMS — REST API

Base URL (local): `http://localhost:8080`
Authentication: `Authorization: Bearer <JWT>` on every endpoint except
`POST /auth/login` and `GET /health`.

All request and response bodies are JSON. Dates use ISO-8601
(`2021-07-21` for dates, `2026-11-17T05:30:00Z` for timestamps).

---

## Authentication

### `POST /auth/login` — public

```http
POST /auth/login
Content-Type: application/json

{ "username": "admin", "password": "<operator password>" }
```

Locally the seeded bootstrap operator is `admin` with the development password
from `app.seed.admin-password`. Deployments set their own value through
`APP_SEED_ADMIN_PASSWORD`.

`200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-11-17T13:30:00Z",
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "Stark Group Administrator",
    "role": "ADMIN"
  }
}
```

`401 Unauthorized` — wrong credentials:

```json
{
  "timestamp": "2026-11-17T05:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password.",
  "path": "/auth/login"
}
```

`400 Bad Request` — blank username or password (`fieldErrors.username`,
`fieldErrors.password`).

### `GET /auth/me` — Bearer required

Returns the current operator profile (`UserResponse`).
`401` when the token is missing, malformed or expired.

---

## Vehicles

### `GET /vehicles?page=0&size=10&sort=vehicleNumber,asc`

| Query parameter | Default | Rules |
|---|---|---|
| `page` | `0` | Negative values are clamped to `0` |
| `size` | `10` | Clamped to `1..100` |
| `sort` | `vehicleNumber,asc` | `<field>,<asc\|desc>`; field must be one of `vehicleNumber`, `company`, `type`, `brand`, `model`, `status`, `createdAt`, `updatedAt` |

`200 OK` — a Spring-Page-shaped payload with exactly these keys:

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

- A page beyond the last page returns `content: []`, `last: true`, and the real
  `totalElements` / `totalPages` (never a 404).
- An unsupported `sort` field returns `400` with an explanatory message.

### `GET /vehicles/{id}`

`200 OK` — one `VehicleResponse`.
`404 Not Found` — `"Vehicle with id 999 was not found."`
`400 Bad Request` — non-numeric id.

### `POST /vehicles`

```http
POST /vehicles
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "vehicleNumber": "dha-ga-11-2001",
  "company": "Stark Construction",
  "type": "PICKUP",
  "brand": "Toyota",
  "model": "Hilux",
  "status": "ACTIVE"
}
```

`201 Created` — the persisted vehicle. `vehicleNumber` and enum values are
normalized; `status` defaults to `ACTIVE` when omitted.

Field validation:

| Field | Rule |
|---|---|
| `vehicleNumber` | required, 4–20 characters, `[A-Za-z0-9-]`, unique (case/whitespace-insensitive) |
| `company` | required, 2–60 characters |
| `type` | required, one of `CAR, SUV, PICKUP, VAN, TRUCK, BUS` |
| `brand`, `model` | required, 2–40 characters |
| `status` | optional, one of `ACTIVE, INACTIVE, MAINTENANCE` |

### `PUT /vehicles/{id}`

Same body as `POST`. `200 OK` with the updated record.
The vehicle may keep its own number; a number owned by a *different* vehicle
returns `409`. `404` when the id does not exist.

### `DELETE /vehicles/{id}`

`204 No Content`, or `404` when the id does not exist.

---

## Errors

Every error uses the same envelope:

```json
{
  "timestamp": "2026-11-17T05:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Vehicle number 'DHA-GA-11-2001' already exists.",
  "path": "/vehicles",
  "fieldErrors": { "vehicleNumber": "Vehicle number already exists." }
}
```

`fieldErrors` is omitted for errors that are not field-specific.

| Status | When | `message` example |
|---|---|---|
| 400 | Bean Validation failure | `Validation failed - company: Company is required; brand: Brand is required` |
| 400 | Malformed JSON | `Malformed JSON request body. Check the field names and value types.` |
| 401 | Missing/invalid/expired token | `Authentication is required to access this resource.` |
| 401 | Bad credentials | `Invalid username or password.` |
| 403 | Insufficient role | `You do not have permission to perform this action.` |
| 404 | Unknown vehicle id | `Vehicle with id 999 was not found.` |
| 405 | Wrong HTTP verb | `Method 'PATCH' is not supported.` |
| 409 | Duplicate `vehicleNumber` | `Vehicle number 'DHA-GA-11-2001' already exists.` |
| 409 | Other SQL constraint violation | `The request conflicts with an existing record (database constraint).` |
| 500 | Unexpected error | `Unexpected server error. Please try again.` |

---

## Health

### `GET /health` — public

```json
{ "status": "UP", "application": "stark-group-vms", "version": "1.0.0" }
```

Used as the Render health check path.

---

## Duplicate Vehicle Number behaviour (detail)

1. **Normalization** — trim, collapse repeated whitespace, upper-case.
2. **Service pre-check** — `existsByVehicleNumberIgnoreCase` →
   `409` with a friendly message plus `fieldErrors.vehicleNumber`.
3. **Database guard** — `ux_vehicles_vehicle_number` unique index.
   If two requests race past the pre-check, the losing insert raises a
   `DataIntegrityViolationException`, which is translated into the same `409`.
4. **Updates** — uniqueness excludes the record being edited, so saving a
   vehicle without changing its number always succeeds.

The rule is proven by `VehicleServiceTest` (logic), `VehicleApiIT` (HTTP) and
`UniqueConstraintIT` (SQL-level), and once more by `scripts/smoke.sh`.
