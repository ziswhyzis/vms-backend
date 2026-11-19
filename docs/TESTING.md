# Testing

Three independent layers verify the system: backend tests, Flutter tests, and a
live REST journey against a running server.

## Measured results (this build)

| Suite | Command | Tests / checks | Result |
|---|---|---|---|
| Backend unit | `mvn test` | 10 | ✅ pass |
| Backend API/integration | same run | 29 | ✅ pass |
| Backend registration | same run | 10 | ✅ pass |
| Backend CORS | same run | 4 | ✅ pass |
| Backend DB constraint | same run | 1 | ✅ pass |
| Backend full journey | same run | 1 | ✅ pass |
| Backend seeder | same run | 7 | ✅ pass |
| **Backend total** | | **62** | **✅ 62/62** |
| Flutter static analysis | `flutter analyze` (frontend repo) | — | ✅ 0 issues |
| Flutter widget/contract | `flutter test` (frontend repo) | 47 | ✅ 47/47 |
| Live REST smoke | `./scripts/smoke.sh` | 28 | ✅ 28/28 |
| Flutter web release build | `flutter build web --release` (frontend repo) | — | ✅ built |

Environment: Flutter 3.50.0 (Dart 3.14), Java 21.0.13, Maven 3.10.1,
Spring Boot 3.5.3, H2 2.3.232.

---

## Backend test matrix

| Class | Kind | What it proves |
|---|---|---|
| `VehicleServiceTest` | Mockito unit | Number normalization; duplicate rejection on create; duplicate rejection on update excluding self; DB-violation translation; every vehicle field copied onto the entity; delete 404; page-size clamping to 100; descending sort; unsafe sort field rejection |
| `VehicleApiIT` | `@SpringBootTest` + MockMvc | 401 without token; required Page JSON keys and exactly 10 rows; `last` flag; out-of-range page; size clamp; unknown sort → 400; sort by company; create → 201 with all vehicle fields; status defaults to ACTIVE; get; 404; non-numeric id → 400; duplicate → 409 + message + field error; case-insensitive duplicate; update onto another number → 409; validation → 400 + field errors; bad pattern → 400; malformed JSON → 400; update persists; delete → 204 then 404; delete missing → 404 |
| `AuthApiIT` | `@SpringBootTest` + MockMvc | login returns token/profile; wrong password → 401; unknown user → 401; blank credentials → 400; `/auth/me` with token; `/auth/me` without token → 401; invalid token → 401; `/health` is public |
| `RegisterApiIT` | `@SpringBootTest` + MockMvc | register → 201 + JWT + profile; new operator can use `/auth/me` and `/vehicles`; can sign in later; password stored as BCrypt; duplicate username → 409 + field error; case-insensitive duplicate; short password, invalid username and blank full name → 400; no token required |
| `CorsApiIT` | `@SpringBootTest` + MockMvc | any `localhost` port is accepted by pattern; the configured Netlify origin is accepted exactly; an unknown origin gets `403` with no `Access-Control-Allow-Origin`; simple requests from allowed origins carry the header |
| `UniqueConstraintIT` | `@DataJpaTest` (real H2) | the unique index rejects a duplicate number even when the service check is bypassed |
| `FullJourneyIT` | `@SpringBootTest` + MockMvc | login → register → list → duplicate 409 → edit → read → delete → 404 → empty last page |
| `DataSeederIT` | `@SpringBootTest` | 48 seeded vehicles with Bangladeshi plate shapes across 8 cities; representative row fields; re-running adds nothing; existing rows are never overwritten; BCrypt admin; no operator when the password is blank |
| `scripts/smoke.sh` | Live HTTP (curl) | the same journey against the packaged jar on `:8080`, plus pagination shape checks |

Run:

```bash
mvn test          # unit + API + integration + journey
mvn package       # produce target/vms-backend-1.0.0.jar
```

Surefire is configured to include both `*Test.java` and `*IT.java`.

---

## Frontend test matrix

| File | Tests | Coverage |
|---|---|---|
| `test/api_client_test.dart` | 7 | Bearer header + query serialization; 409 `fieldErrors` mapping; 400 validation mapping; 204 → null; unreachable backend → status 0; Page JSON key set; fixture sanity |
| `test/vehicle_model_test.dart` | 5 | `Vehicle.fromJson`; request-body normalization and exact key set; enum fallbacks; `VehiclePage` 7-field parsing; visible-range math; empty page |
| `test/login_screen_test.dart` | 3 | Empty-field validation; successful login callback; backend 401 message banner |
| `test/vehicles_screen_test.dart` | 12 | API-driven list rendering; server-side next page; page-size reset to 0; sort parameter evolution; delete confirm → API call → empty state; cancel keeps data; empty state; error banner + retry; responsive table → cards; duplicate 409 surfaced on the field; exact POST body contract; edit dialog prefill + PUT body |
| `test/pagination_bar_test.dart` | 5 | Range/counter text; prev/next callbacks; next disabled on last page; prev disabled on first page; empty-register labels |
| `test/session_store_test.dart` | 9 | Session round-trip; corrupt JSON and wrongly typed storage are treated as signed out and cleared; boot shows sign-in with no session; never strands the splash when storage is corrupt; restores a stored session into the fleet register; a rejected token signs out and clears storage |

Run in the frontend repository:

```bash
flutter analyze
flutter test
```

The tests use `MockClient` from `package:http/testing`, so no running backend
or browser is required. `shared_preferences` is mocked with
`SharedPreferences.setMockInitialValues({})`.

---

## Live smoke test

```bash
# terminal 1
java -jar target/vms-backend-1.0.0.jar

# terminal 2
./scripts/smoke.sh
```

Checks executed (28):

1. `GET /health` public → 200
2. login with wrong password → 401
3. login with the bootstrap operator (`ADMIN_USER`/`ADMIN_PASS`, default `admin`/`admin123` locally) → 200 + JWT
4. unauthenticated `/vehicles` → 401
5. `POST /auth/register` → 201
6. register returns a JWT
7. duplicate username → 409
8. new operator can sign in → 200
9. paginated list → 200
10. Page payload contains `content, number, size, totalElements, totalPages, last`
11. requested page size honoured
12. out-of-range page → empty content
13. register vehicle → 201
14. number normalized to uppercase
15. duplicate vehicle number → 409
16. 409 carries an error message
17. 409 identifies `vehicleNumber`
18. invalid payload → 400
19. 400 carries a validation message
20. 400 carries `fieldErrors.company`
21. `GET /vehicles/{id}` → 200
22. `PUT /vehicles/{id}` → 200
23. update persisted model
24. update persisted status
25. update persisted company
26. `DELETE /vehicles/{id}` → 204
27. deleted vehicle → 404
28. result summary must be 0 failures

---

## Optional real-browser E2E

Requires Chrome + chromedriver and a running backend.

```bash
# terminal 1
java -jar target/vms-backend-1.0.0.jar

# terminal 2
chromedriver --port=4444

# terminal 3 (frontend repository)
flutter drive \
  --driver=test_driver/integration_test.dart \
  --target=integration_test/login_flow_test.dart \
  -d chrome \
  --dart-define=API_BASE_URL=http://localhost:8080
```

The driver signs in with the bootstrap operator and asserts that the Fleet Register,
the Register Vehicle action and the pagination bar all render in a real browser.

## Test data hygiene

- Backend tests use a dedicated in-memory H2 database
  (`src/test/resources/application-test.yml`, `ddl-auto=create-drop`) and
  truncate tables in `@BeforeEach`. They never touch the development database.
- The smoke script creates and deletes exactly one vehicle per run
  (`SMOKE-<time>`), leaving the seeded fleet unchanged.
- Development data lives in `data/` and is git-ignored.
