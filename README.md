# LifeClues — Backend (Step 1: Foundation + Account)

The private, calm, book-like memory journal. This is the **backend** for Step 1:
a secure account system. No frontend pages yet — you test with `curl` commands.

## What you can do in this step

- Create an account (email or username, password)
- Log in (email **or** username) and get secure tokens
- Stay logged in safely (short-lived **access token** + long-lived **refresh token**)
- View your own profile
- Edit your own profile
- Change your password
- Log out (kills your refresh token)
- **You can never see or touch another user's data** — there is no endpoint
  that reads another user's profile. The server always figures out "who you are"
  from your token, never from what the client says.

## The database

- **PostgreSQL** (Neon, database `journal_db`)
- Schema is managed by **Flyway migrations** (`src/main/resources/db/migration`).
- Credentials come only from your private `.env` file.

## 1. Configure your secrets

```bash
cp .env.example .env
```

Open `.env` and fill in:

```bash
LC_DB_URL=jdbc:postgresql://YOUR_HOST:5432/YOUR_DB?sslmode=require
LC_DB_USER=YOUR_USER
LC_DB_PASSWORD=YOUR_PASSWORD
LC_JWT_SECRET=openssl rand -base64 64   # run this command and paste the result
```

`.env` is private and git-ignored. **Never commit it.**

## 2. Run the server

```bash
./dev.sh
```

The server starts at `http://localhost:8080`. On first run, Flyway
automatically creates the tables. On the last run, it printed
`Started LifecluesApplication`.

## 3. Try every endpoint (copy-paste commands)

Register a new account:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","username":"you","password":"yoursecret123"}'
```

Log in (works with email **or** username):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"you@example.com","password":"yoursecret123"}'
```

Save the `accessToken` and `refreshToken` from the reply. In the commands
below, replace `YOUR_ACCESS_TOKEN` / `YOUR_REFRESH_TOKEN` with those values.

View your profile:

```bash
curl http://localhost:8080/api/me -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

Edit your profile:

```bash
curl -X PUT http://localhost:8080/api/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Your Nice Name","bio":"A little about you"}'
```

Change your password (old password required):

```bash
curl -X POST http://localhost:8080/api/me/change-password \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"yoursecret123","newPassword":"brandnewsecret456"}'
```

Get a fresh access token when the old one expires:

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"YOUR_REFRESH_TOKEN"}'
```

Log out (revokes the refresh token):

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"YOUR_REFRESH_TOKEN"}'
```

## 4. Run the automated tests

```bash
./dev.sh test
```

The full security tests use **Testcontainers** (they start a throwaway
PostgreSQL inside Docker), so Docker must be running. If Docker is not
available the database tests are skipped — you can still verify everything
yourself with the curl commands above.

## Configuration options (all optional)

| Variable | Default | Meaning |
| --- | --- | --- |
| `LC_PORT` | `8080` | Port the server listens on |
| `LC_ACCESS_EXPIRY_MINUTES` | `15` | How long an access token lasts |
| `LC_REFRESH_EXPIRY_DAYS` | `7` | How long a refresh token lasts |
| `LC_CORS_ORIGINS` | `http://localhost:5173` | Allowed browser origins (for the future frontend) |

## How the code is organized

Each feature owns its own folder (controller → service → repository → entity
→ dto → mapper). Controllers stay thin; business logic lives in services;
only DTOs cross the API boundary — never database entities.

```
src/main/java/in/abdulmajid/lifeclues/
├── common/     error handling + shared response types
├── config/     web configuration
├── security/   JWT tokens, login filters, "who am I" lookup
├── auth/       login, refresh, logout
└── account/    signup, profile, change password
```

Anti-patterns deliberately avoided: no `/users/{id}` endpoint, no trusting
client-supplied user IDs, no exposing entities over REST, no secrets in code.

## Security notes

- Passwords are hashed with **BCrypt** (strength 12) — never stored in plain text.
- Access tokens are **JWT**, signed with the secret in `.env`.
- Refresh tokens are random strings; only their **SHA-256 hash** is stored in
  the database (losing the database does not leak usable tokens).
- A refresh token can be used exactly once (it is rotated on every refresh).
- Invalid/expired/missing tokens get a clean JSON `401`.

## What's next (future steps)

- V2: write memories (story, date, people, places, tags, mood, photos, drafts,
  trash with 30-day auto-delete)
- V3: rediscover memories by clues (search by tags/places/people/mood/dates,
  `+` require-all search)
- V4: memory gifting between users
- Later: Docker packaging, then Redis/Kafka only if and when the app actually
  needs them at scale.