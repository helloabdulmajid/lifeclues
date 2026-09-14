# LifeClues — Backend

**Small Clues. Big Memories.**

LifeClues is a private memory journal — a calm, book-like space where you write down the moments that matter and rediscover them later through small clues. This repository is the **backend**: a Spring Boot REST API that handles user accounts, authentication, memories, tags, and everything in between.

I built this to learn Spring Boot properly — not just copy-paste a tutorial, but actually understand how a production-quality API is structured. Security, validation, clean architecture, database migrations, testing — the real stuff.

---

## Why I built this

Most journal apps feel like productivity tools. LifeClues is supposed to feel like opening an old notebook and finding a note you forgot you wrote. I wanted to build the whole thing myself — frontend, backend, database, deployment — so I understand every piece. The backend is where most of the hard problems live: authentication, data isolation, schema management, validation, and making sure nobody can accidentally (or deliberately) touch someone else's data.

---

## What the backend does right now

- **User registration** with email and username, automatic email verification
- **Login** by email or username with JWT access + refresh token pair
- **Token rotation** — every refresh gives a new pair, old one is invalidated
- **Profile management** — display name, bio, birthday, city, profession, and more
- **Time format preference** — 12-hour or 24-hour
- **Password change** (requires current password) and **password reset** via email
- **Full memory CRUD** — create, read, update with title, content, date, time, and tags
- **Drafts and completed memories** — drafts don't need tags, completed ones do
- **Soft-delete with trash** — memories go to trash, stay 30 days, then get purged automatically
- **Tags** — case-insensitive, user-scoped, max 50 per memory
- **Account deletion** — permanently removes user and all data in one transaction
- **CORS configuration** ready for the React frontend

---

## Tech stack

| Technology | What I'm using it for |
|---|---|
| **Java 21** | Language — records, pattern matching, modern JVM |
| **Spring Boot 4.1.1** | Application framework — web, security, validation, mail |
| **Spring Data JPA** | Database access — repositories, queries, entity mapping |
| **Hibernate** | ORM — maps Java entities to PostgreSQL tables |
| **PostgreSQL** | Primary database (using Neon managed PostgreSQL) |
| **Flyway** | Database migrations — versioned SQL files that run in order |
| **Spring Security** | Authentication and authorization — JWT filter, password hashing |
| **JJWT 0.12.6** | JWT token creation and validation (HMAC-SHA256) |
| **Lombok** | Reduces boilerplate — `@Getter`, `@Setter`, `@NoArgsConstructor` |
| **BCrypt** | Password hashing (strength 12) |
| **Jakarta Validation** | Request validation — `@NotBlank`, `@Email`, `@Size`, `@Past` |
| **Spring Mail** | Sending verification and password reset emails |
| **HikariCP** | Connection pooling (built into Spring Boot) |
| **Maven** | Build tool — dependency management, compilation, packaging |
| **Testcontainers** | Integration tests with real PostgreSQL in Docker |
| **JUnit 5** | Test framework |
| **MockMvc** | HTTP layer testing without starting a real server |

---

## What I learned from each major technology

### Spring Boot

I chose Spring Boot because it handles the boring stuff (server configuration, dependency injection, auto-configuration) so I can focus on building actual features. The `@SpringBootApplication` annotation wires everything together. I learned that the magic only works well when you understand what's happening underneath — especially with security filters and JPA proxies.

### Spring Data JPA

Writing repository interfaces and letting Spring generate the queries felt like magic at first. Then I hit edge cases (case-insensitive unique constraints, soft-delete filtering, pagination sorting) and had to write `@Query` annotations. I learned that JPA repositories are powerful but you need to understand the SQL they generate.

### PostgreSQL

I'm using PostgreSQL on Neon (managed hosting). I chose it for its reliability, JSON support (if I need it later), and because Flyway + PostgreSQL is a well-tested combination. The database handles cascade deletes across all tables, which means a single `DELETE FROM users WHERE id = ?` cleans up everything through foreign key chains.

### Flyway database migrations

This is one of the most important things I learned. I started with `ddl-auto=update` (Hibernate auto-generates schema from entities), and it silently destroyed my cascade constraints, CHECK constraints, and indexes. I spent hours debugging missing foreign keys before understanding the problem.

Flyway fixes this. Every schema change is a numbered SQL file (V1, V2, V3...). Flyway tracks which ones have run in a `flyway_schema_history` table. On startup, it runs only the new ones. The schema is **predictable, version-controlled, and reproducible**. No surprises.

I set `ddl-auto=none` and never looked back. Migrations V5 through V10 in this project exist specifically to repair damage from accidental `ddl-auto=create` runs during development.

### JWT authentication

I implemented stateless JWT auth with two token types:
- **Access tokens** (JWT, 15-minute expiry) — short-lived, signed with HMAC-SHA256, carry the user ID and email in the payload
- **Refresh tokens** (opaque random strings, 7-day expiry) — stored as SHA-256 hashes in the database, used exactly once (rotation)

The JWT filter sits before Spring Security's `UsernamePasswordAuthenticationFilter`. It extracts the Bearer token from the `Authorization` header, validates it, and sets the security context. If the token is missing or invalid, the request proceeds unauthenticated and Spring returns 401.

### Refresh-token/session security

Refresh tokens are never stored in plaintext — only their SHA-256 hash goes into the database. This means even if someone dumps the database, they can't use the tokens. Every refresh operation rotates the token: old one is revoked, new pair is issued. Password reset revokes all refresh tokens for that user. This is the same pattern used by major services.

### Maven

I use the Maven Wrapper (`./mvnw`) so nobody needs to install Maven separately. The `pom.xml` manages all dependencies and build plugins. The Lombok annotation processor is configured in the compiler plugin to work with both main and test compilation.

### Java 21

Records for DTOs (`LoginRequest`, `UserResponse`, etc.) — immutable, concise, auto-generated equals/hashCode/toString. No more writing boilerplate DTO classes. `Instant` for timestamps, `LocalDate` for dates, `UUID` for primary keys.

### Testing

I use **Testcontainers** to spin up a real PostgreSQL database in Docker for integration tests. Every test class cleans the database before running. This catches problems that H2 or mocks would miss — real SQL, real constraints, real cascade behavior.

- `JwtServiceTest` — 6 unit tests for token generation, validation, tampering, expiry
- `AuthFlowTest` — 14 integration tests covering the complete auth lifecycle
- `MemoryFlowTest` — 18 integration tests for memory CRUD, tags, trash, user isolation
- `AccountFlowTest` — 4 integration tests for profile updates and password changes

---

## Backend architecture

I structured this as a classic layered Spring Boot application. Each feature owns its own folder:

```
Controller → Service → Repository → Entity
                  ↕
              DTO / Mapper
```

### Why this structure?

**Controllers stay thin.** They handle HTTP concerns (extracting parameters, validating requests, returning responses) and delegate everything else to services. A controller should never contain business logic.

**Services contain the business rules.** This is where validation happens beyond what annotations can express — checking ownership, enforcing limits, resolving tags, deciding what's allowed. Services are `@Transactional` so database operations are atomic.

**Repositories are interfaces.** Spring Data JPA generates the implementation. Custom queries go in `@Query` annotations or method names.

**Entities are the database representation.** They stay in the `entity` package and never cross the API boundary. Controllers and clients see DTOs.

**DTOs are the API contract.** Request records define what clients can send. Response records define what clients receive. The API shape can evolve independently of the database schema.

**Mappers convert between entities and DTOs.** Simple, explicit mapping methods. No reflection, no magic frameworks.

### Security architecture

```
Request → JwtAuthenticationFilter → SecurityContext → Controller
                   ↓
            JwtService validates token
            UserDetailsService loads user
            SecurityContextHolder stores authentication
```

- `@CurrentUser` annotation extracts the authenticated user from the security context
- Every memory/tag query is scoped by `user_id` — extracted from the JWT, never from the client
- No `/users/{id}` endpoints exist — user isolation is built into the query layer

### Database ownership

All foreign keys use `ON DELETE CASCADE`. When a user is deleted:

```
DELETE FROM users WHERE id = ?
  ├── refresh_tokens    (CASCADE)
  ├── auth_tokens       (CASCADE)
  ├── memories          (CASCADE)
  │     └── memory_tags (CASCADE)
  └── tags              (CASCADE)
        └── memory_tags (CASCADE)
```

No orphaned data. No manual cleanup needed.

---

## API overview

### Authentication (`/api/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Create account, sends verification email |
| POST | `/api/auth/login` | No | Login by email or username (must be verified) |
| POST | `/api/auth/refresh` | No | Rotate refresh token, get new pair |
| POST | `/api/auth/logout` | No | Revoke refresh token |
| POST | `/api/auth/verify-email` | No | Verify email (single-use token) |
| POST | `/api/auth/resend-verification` | No | Resend verification email |
| POST | `/api/auth/forgot-password` | No | Send password reset email |
| POST | `/api/auth/reset-password` | No | Reset password, revokes all sessions |

### Account (`/api`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/me` | Yes | Get current user profile |
| PUT | `/api/me` | Yes | Update profile fields |
| POST | `/api/me/change-password` | Yes | Change password |
| DELETE | `/api/me` | Yes | Delete account and all data |

### Memories (`/api/memories`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/memories` | Yes | List memories (paginated, sorted by event date) |
| GET | `/api/memories/trash` | Yes | List trashed memories |
| POST | `/api/memories` | Yes | Create memory (defaults to draft) |
| GET | `/api/memories/{id}` | Yes | Get single memory |
| PUT | `/api/memories/{id}` | Yes | Update memory |
| PATCH | `/api/memories/{id}/status` | Yes | Toggle draft ↔ completed |
| DELETE | `/api/memories/{id}` | Yes | Move to trash (soft delete) |
| POST | `/api/memories/{id}/restore` | Yes | Restore from trash |
| DELETE | `/api/memories/{id}/permanent` | Yes | Permanently delete |
| DELETE | `/api/memories/trash` | Yes | Empty entire trash |

### Tags (`/api/tags`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/tags` | Yes | List all user tags |

---

## Project structure

```
lifeclues/
├── dev.sh                          # Dev script (loads .env, validates, runs)
├── pom.xml                         # Maven config + dependencies
├── .env.example                    # Template for required environment variables
└── src/
    ├── main/
    │   ├── java/in/abdulmajid/lifeclues/
    │   │   ├── LifecluesApplication.java     # Entry point + .env loader
    │   │   ├── account/
    │   │   │   ├── controller/               # AccountController (profile, delete)
    │   │   │   ├── dto/                      # RegisterRequest, UserResponse, etc.
    │   │   │   ├── entity/                   # User entity
    │   │   │   ├── mapper/                   # UserMapper
    │   │   │   ├── repository/               # UserRepository
    │   │   │   └── service/                  # AccountService
    │   │   ├── auth/
    │   │   │   ├── controller/               # AuthController (login, register, etc.)
    │   │   │   ├── dto/                      # LoginRequest, AuthResponse, etc.
    │   │   │   ├── entity/                   # RefreshToken, AuthToken
    │   │   │   ├── repository/               # RefreshTokenRepository, AuthTokenRepository
    │   │   │   └── service/                  # AuthService (token issuance, email flows)
    │   │   ├── memory/
    │   │   │   ├── controller/               # MemoryController, TagController
    │   │   │   ├── dto/                      # MemoryRequest, MemoryResponse, etc.
    │   │   │   ├── entity/                   # Memory, Tag
    │   │   │   ├── mapper/                   # MemoryMapper, TagMapper
    │   │   │   ├── repository/               # MemoryRepository, TagRepository
    │   │   │   └── service/                  # MemoryService, TagService, MemorySweeper
    │   │   ├── security/
    │   │   │   ├── JwtService                # Token generation + validation
    │   │   │   ├── JwtAuthenticationFilter   # Extracts Bearer token, sets context
    │   │   │   ├── UserPrincipal             # Implements UserDetails
    │   │   │   ├── AppUserDetailsService     # Loads user by email/username
    │   │   │   ├── SecurityConfig            # HTTP security rules + CORS
    │   │   │   ├── CurrentUser               # Custom annotation
    │   │   │   ├── CurrentUserArgumentResolver
    │   │   │   ├── RestAuthenticationEntryPoint  # 401 JSON response
    │   │   │   └── RestAccessDeniedHandler       # 403 JSON response
    │   │   ├── common/
    │   │   │   ├── dto/                      # MessageResponse, ApiError
    │   │   │   └── exception/               # GlobalExceptionHandler + custom exceptions
    │   │   ├── config/
    │   │   │   └── WebMvcConfig              # Registers argument resolver
    │   │   └── mail/
    │   │       └── MailService               # Verification + reset emails
    │   └── resources/
    │       ├── application.properties         # All configuration
    │       └── db/migration/                  # V1–V12 SQL migration files
    └── test/
        └── java/in/abdulmajid/lifeclues/
            ├── AbstractIntegrationTest        # Base class with Testcontainers
            ├── JwtServiceTest                 # 6 tests
            ├── AuthFlowTest                   # 14 tests
            ├── MemoryFlowTest                 # 18 tests
            └── AccountFlowTest                # 4 tests
```

---

## Getting started

### Prerequisites

- **Java 21** (I use Oracle JDK 23, but 21+ works)
- **Docker** (for Testcontainers in integration tests)
- **PostgreSQL** database (local or managed like Neon)

### 1. Clone and configure

```bash
git clone https://github.com/helloabdulmajid/lifeclues.git
cd lifeclues
cp .env.example .env
```

Edit `.env` and fill in your database credentials and JWT secret:

```bash
# Generate a JWT secret: openssl rand -base64 64
LC_DB_URL=jdbc:postgresql://YOUR_HOST:5432/YOUR_DB
LC_DB_USER=YOUR_USER
LC_DB_PASSWORD=YOUR_PASSWORD
LC_JWT_SECRET=YOUR_GENERATED_SECRET
```

### 2. Run

```bash
./dev.sh
```

The server starts at `http://localhost:8080`. Flyway runs all migrations automatically on first boot. You'll see `Started LifecluesApplication` when it's ready.

### 3. Run tests

```bash
./dev.sh test
```

Tests use Testcontainers (spins up PostgreSQL in Docker). Docker must be running. If Docker is unavailable, the integration tests are skipped but compilation still works.

---

## Environment variables

### Required

| Variable | Description |
|---|---|
| `LC_DB_URL` | PostgreSQL JDBC URL |
| `LC_DB_USER` | Database username |
| `LC_DB_PASSWORD` | Database password |
| `LC_JWT_SECRET` | Secret key for signing JWT tokens (Base64, min 32 bytes) |

### Optional

| Variable | Default | Description |
|---|---|---|
| `LC_PORT` | `8080` | Server port |
| `LC_ACCESS_EXPIRY_MINUTES` | `15` | Access token lifetime |
| `LC_REFRESH_EXPIRY_DAYS` | `7` | Refresh token lifetime |
| `LC_CORS_ORIGINS` | `http://localhost:5173,...` | Allowed frontend origins |
| `LC_TRASH_RETENTION_DAYS` | `30` | Days before trashed memories are purged |
| `LC_MAIL_HOST` | `smtp.gmail.com` | SMTP server |
| `LC_MAIL_PORT` | `587` | SMTP port |
| `LC_MAIL_USERNAME` | (blank) | SMTP login (leave blank to skip email) |
| `LC_MAIL_PASSWORD` | (blank) | SMTP password |
| `LC_MAIL_FROM` | (blank) | "From" address on emails |
| `LC_APP_BASE_URL` | (blank) | Fixed base URL for emailed links |
| `LC_VERIFY_TOKEN_EXPIRY_MINUTES` | `30` | Email verification token lifetime |
| `LC_RESET_TOKEN_EXPIRY_MINUTES` | `30` | Password reset token lifetime |

---

## Production build

```bash
./mvnw clean package -DskipTests
```

The JAR is at `target/lifeclues-0.0.1-SNAPSHOT.jar`. Run it with:

```bash
java -jar target/lifeclues-0.0.1-SNAPSHOT.jar
```

Or set the environment variables and run directly — the `.env` loader in `LifecluesApplication.java` handles them on startup.

---

## What I'm currently working on

- Improving test coverage for edge cases
- Learning more about Spring Security's authorization model
- Understanding Hibernate's lazy loading behavior and N+1 query prevention

---

## Possible future improvements

> These are ideas, not promises. I'm building this to learn with to ship a product mindset.

- Docker packaging for the backend
- Rate limiting on auth endpoints
- Full-text search with PostgreSQL's `tsvector`/`tsquery`
- Memory sharing between users
- Image attachments for memories
- Redis for caching (only if it's actually needed)
- API documentation with SpringDoc/Swagger

---

## License

Not specified yet.

---

Built with care by [Abdul Majid](https://github.com/helloabdulmajid).
