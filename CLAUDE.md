# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A leave-management system built as a take-home exam. The goal is to fix bugs, add an approve endpoint, refactor the fat controller, and improve the Angular frontend. Intentional defects exist in both backend and frontend — see the task list in README.html.

## Commands

### Full stack (recommended)
```bash
docker compose up --build   # starts PostgreSQL + API together
# API: http://localhost:5080   Swagger: http://localhost:5080/swagger-ui.html
```

### Backend only (requires Postgres already running, e.g. `docker compose up db`)
```bash
cd backend
mvn spring-boot:run
```

### Backend tests (Docker required — uses Testcontainers for a real PostgreSQL)
```bash
cd backend
mvn test                                         # all tests
mvn test -Dtest=LeaveRequestsTests               # single class
mvn test -Dtest=LeaveRequestsTests#methodName    # single method
```

### Frontend
```bash
cd frontend
npm install
npm start          # http://localhost:4200
ng test            # Karma/Jasmine unit tests
```

## Architecture

### Backend (`backend/src/main/java/com/example/leavemanagement/`)

```
controller/   REST layer — currently fat (business logic + DB access + validation all here)
model/        JPA entities: Employee, LeaveRequest, and enums LeaveType / LeaveStatus
repository/   Spring Data JPA interfaces: EmployeeRepository, LeaveRequestRepository
dto/          CreateLeaveRequestDto (input for POST /api/leave-requests)
config/       DataSeeder (seeds two employees on first startup), WebConfig (CORS)
```

Key facts:
- Server port: **5080** (`application.properties`).
- `LeaveType` and `LeaveStatus` enums are persisted as **ordinal integers** (`EnumType.ORDINAL`). The frontend maps 0/1/2 manually in `typeLabel`/`statusLabel`.
- Entities are returned directly from controllers (no response DTOs). Circular `Employee ↔ LeaveRequest` is broken via `@JsonIgnoreProperties` on the `@ManyToOne` in `LeaveRequest`.
- `spring.jpa.open-in-view=false` — Hibernate session is closed before serialization, so lazy collections must be initialized inside a transaction.
- `ddl-auto: update` — Hibernate manages the schema; no migration files.
- `DataSeeder` seeds Dana Levi (quota 20, 18 days already approved) and Yossi Cohen (quota 14) on first startup only.

### Known intentional bugs to fix

1. **Balance bug** (`LeaveRequestsController.create`): the quota check compares only the new request's days against `annualQuota`, ignoring already-used days (`used` variable is computed but never added to `days` in the comparison).
2. **SQL injection** (`/search` endpoint): native query built by string concatenation — must be replaced with a parameterized query or a Spring Data derived method.
3. **Missing endpoint**: `POST /api/leave-requests/{id}/approve` — needs to handle concurrent approvals safely (pessimistic locking or `SELECT FOR UPDATE` inside a `@Transactional` method).
4. **Fat controller**: `LeaveRequestsController` mixes HTTP mapping, business logic, and repository calls. Refactor by extracting a service layer.

### Frontend (`frontend/src/app/`)

```
leave-requests/   Main component — currently calls HttpClient directly, uses `any`, no service layer
models/           leave-request.model.ts (TypeScript types)
app.component     Root shell
app.config        Standalone app bootstrap with HttpClient provider
```

Key facts:
- Angular 17 standalone components (no NgModules).
- API base URL is hard-coded to `http://localhost:5080` in the component.
- The approve button calls `POST /{id}/approve` then blindly reloads all data — no loading/error/success state.
- `*ngFor` and pipes (`date`) are used in the template; `CommonModule` is imported for them.

## Test infrastructure

Tests live in `backend/src/test/`. They use `@SpringBootTest` (full context) + Testcontainers (`PostgreSQLContainer`) and `@DynamicPropertySource` to point the app at the throwaway container. Docker must be running. Tests wire the controller directly (not via MockMvc), so they are integration tests, not unit tests.
