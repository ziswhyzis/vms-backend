# Architecture

## System shape

```
┌──────────────────────────┐         HTTPS / JSON          ┌──────────────────────────┐
│      Flutter Web          │  ─────────────────────────▶  │    Spring Boot 3.5        │
│  (Netlify / localhost)    │   Authorization: Bearer JWT   │  (Render / localhost:8080)│
│                            │  ◀─────────────────────────  │                           │
│  login · register · CRUD  │        REST + Page JSON       │  JWT security · JPA       │
└──────────────────────────┘                                └────────────┬─────────────┘
                                                                          │ JDBC / SQL
                                                                          ▼
                                                              ┌──────────────────────────┐
                                                              │  SQL database             │
                                                              │  H2 file (dev)            │
                                                              │  PostgreSQL (prod)        │
                                                              └──────────────────────────┘
```

There are exactly two deployable artifacts: a static Flutter Web bundle and a
stateless Spring Boot API. The database is the only stateful component.

## Backend layers

```
HTTP request
   │
   ▼
SecurityFilterChain        /health, /auth/login public; everything else needs a valid JWT
   │  JwtAuthFilter        verifies signature + expiry, loads the operator, sets SecurityContext
   ▼
Controller                 VehicleController / AuthController: HTTP status + DTO mapping only
   │
   ▼
Service                    VehicleService: normalization, duplicate-number rule, sort allow-list, page clamping
   │
   ▼
Repository                 Spring Data JPA (Page<Vehicle>, existsByVehicleNumberIgnoreCase…)
   │
   ▼
SQL database               unique index ux_vehicles_vehicle_number is the final guarantee
```

Cross-cutting:

- `GlobalExceptionHandler` converts exceptions into the single error envelope
  (`timestamp`, `status`, `error`, `message`, `path`, optional `fieldErrors`).
- `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` emit JSON `401`/`403`
  instead of Spring's default HTML.
- `DataSeeder` seeds `admin` + 24 vehicles only when the tables are empty.

### Security model

| Aspect | Decision |
|---|---|
| Authentication | Stateless JWT (HS256, `jjwt` 0.12.6), subject = username |
| Password storage | BCrypt (`BCryptPasswordEncoder`) |
| Token transport | `Authorization: Bearer <token>` |
| Session | None — `SessionCreationPolicy.STATELESS` |
| Token lifetime | 480 minutes (configurable) |
| Public endpoints | `POST /auth/login`, `GET /health`, OPTIONS preflight, H2 console (dev) |
| CORS | Explicit origin allow-list from `APP_CORS_ALLOWED_ORIGINS`; wildcard patterns (`http://localhost:[*]`) cover random dev-server ports, exact origins cover production |

The Flutter client stores the token in browser `localStorage` (through
`shared_preferences`), attaches it to every request, restores the session by
calling `/auth/me` on reload, and signs out automatically on any `401` from an
authenticated request.

### Duplicate Vehicle Number rule

Two independent layers:

1. **Business layer** — `VehicleService` normalizes the number
   (`trim`, collapse whitespace, upper-case), then checks
   `existsByVehicleNumberIgnoreCase` (or `…AndIdNot` on update) and throws
   `DuplicateVehicleNumberException` → `409 Conflict` with a field error.
2. **Database layer** — `ux_vehicles_vehicle_number` unique index. Any insert
   that bypasses the service (concurrent request, raw SQL) fails the constraint,
   and `VehicleService` translates `DataIntegrityViolationException` into the
   same `409`.

`UniqueConstraintIT` intentionally saves a duplicate straight through the
repository to prove layer 2 exists.

### Pagination and sorting

- Controller accepts `page` (default 0), `size` (default 10), `sort`
  (default `vehicleNumber,asc`).
- `VehicleService` clamps `page >= 0`, `size` into `1..100`, and validates the
  sort field against an allow-list before building a `PageRequest`.
- `VehicleRepository.findAll(pageable)` executes the SQL query.
- `PageResponse.from(page)` projects Spring Data's `Page` into exactly
  `content, number, size, totalElements, totalPages, last`, so the client
  contract never depends on Spring's internal `PageImpl` JSON shape.

### Database schema

```sql
users
  id              bigint identity primary key
  username        varchar(60)  not null, unique (ux_users_username)
  password_hash   varchar(100) not null          -- BCrypt
  full_name       varchar(80)  not null
  role            varchar(24)  not null          -- ADMIN, ...
  created_at      timestamp    not null

vehicles
  id              bigint identity primary key
  vehicle_number  varchar(20) not null, unique (ux_vehicles_vehicle_number)
  company         varchar(60) not null            -- Stark Group business unit
  type            varchar(16) not null            -- CAR|SUV|PICKUP|VAN|TRUCK|BUS
  brand           varchar(40) not null
  model           varchar(40) not null
  status          varchar(16) not null            -- ACTIVE|INACTIVE|MAINTENANCE
  created_at      timestamp   not null
  updated_at      timestamp   not null
```

Hibernate `ddl-auto=update` manages the schema in both profiles (adequate at
this size; a larger rollout would use Flyway or Liquibase migrations).

## Frontend architecture

```
main.dart
  └── StarkVmsApp                 MaterialApp + theme
        └── SessionGate           restores token → Login or Vehicles
              ├── LoginScreen     AuthService.login → localStorage
              └── VehiclesScreen  VehicleService → paginated list
                    ├── AppShell       250px navy sidebar / mobile drawer
                    ├── VehicleFormDialog    add + edit, server field errors
                    ├── ConfirmDeleteDialog  destructive confirmation
                    └── PaginationBar        page, size, prev/next
```

- **Layer separation** — screens never touch `http` directly. `ApiClient` owns
  HTTP, JSON, timeouts, auth headers, and error translation; `AuthService` and
  `VehicleService` own the REST endpoints; screens own presentation and
  navigation.
- **State** — plain `StatefulWidget` + service injection (no third-party state
  manager). Each service is constructed once in `main.dart` and passed down,
  which keeps the widget tests dependency-injectable with a mocked `http.Client`.
- **Error routing** — server `message` is displayed in an inline banner, and
  `fieldErrors` are pushed into the matching form validator, so a duplicate
  number appears under the Vehicle Number input rather than in a generic toast.
- **Responsive rules** — sidebar collapses at `< 1000 px`; the register switches
  from `DataTable` to cards at `< 900 px`; the login page switches from the
  split layout to a single column at `< 980 px`; dialog fields stack at
  `< 520 px`.
- **Session persistence** — `SessionStore` writes token + profile to
  `localStorage`, `SessionGate` calls `/auth/me` on boot, and any authenticated
  `401` clears the session and returns to login.

### Design system

The palette is sampled from the Stark Group brand artwork:

| Token | Hex | Usage |
|---|---|---|
| Navy | `#00081C` | Shell, brand panel, snackbars |
| Navy soft | `#00102E` | Gradients |
| Cyan | `#00B8F0` | Focus, accents, active nav |
| Cyan deep | `#00779C` | Primary buttons (contrast-safe) |
| Lime | `#66D93F` | Active status |
| Amber | `#F2A93B` | Maintenance status |
| Red | `#FF4D5E` | Errors, destructive actions |
| Canvas | `#F5F7FA` | Page background |
| Line | `#DCE3EC` | Borders and table dividers |

Material 3 theme, 10–18 px radii, uppercase 10–11 px tracked section labels,
tabular data table rows 60–68 px high.

## Request flow: registering a vehicle

1. Operator opens **Register Vehicle** → `VehicleFormDialog` validates locally
   (required fields, 4–20 character number, 2–60 character company).
2. `VehicleService.createVehicle` → `ApiClient.post('/vehicles')` with the
   bearer token.
3. `VehicleController` validates the DTO (`@Valid`); failures → `400` with
   `fieldErrors`.
4. `VehicleService` normalizes the number and checks uniqueness → `409`
   with `fieldErrors.vehicleNumber` if taken.
5. JPA inserts the row; the SQL unique index is the race-condition guard.
6. `201 Created` → dialog closes → list reloads from page 0 (sort
   `createdAt,desc`) → new vehicle is visible → snackbar confirms.

## Deployment topology

| Component | Host | Notes |
|---|---|---|
| Flutter Web | Netlify | `netlify.toml` + `netlify-build.sh`, `API_BASE_URL` env var |
| Spring Boot API | Render (Docker) | `Dockerfile`, health check `/health`, `prod` profile |
| PostgreSQL | Render managed database | Credentials injected through `render.yaml` |
| CORS | Render env var | `APP_CORS_ALLOWED_ORIGINS=https://<site>.netlify.app` |

Local development mirrors this with H2 file mode, no external services required.
