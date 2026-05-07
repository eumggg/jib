# JIB Backend API

Node.js + TypeScript REST API with PostgreSQL/PostGIS.

## Prerequisites

- Node.js 20+
- Docker + Docker Compose (for local PostgreSQL/PostGIS)
- Firebase project with Admin SDK access

## Setup

1. Start the database:
   ```sh
   # From repo root
   docker compose up -d db
   ```
2. Copy the example env file:
   ```sh
   cp .env.example .env
   ```
3. Fill in `.env`:
   - `DATABASE_URL` — use default `postgres://jib:jib_dev@localhost:5432/jib` for local docker
   - `FIREBASE_PROJECT_ID` — your Firebase project
   - `GOOGLE_APPLICATION_CREDENTIALS` — path to your Firebase service account JSON
4. Install dependencies:
   ```sh
   npm install
   ```
5. Apply database migrations:
   ```sh
   npm run migrate:up
   ```
6. Start dev server with hot-reload:
   ```sh
   npm run dev
   ```

Or run the whole stack (API + DB) with one command from the repo root:
```sh
docker compose up --build
```

## Scripts

| Command | Description |
|---------|-------------|
| `npm run build` | Compile TypeScript → `dist/` |
| `npm start` | Run compiled output |
| `npm run dev` | Development server with hot-reload |
| `npm run lint` | ESLint check |
| `npm test` | Jest unit tests |
| `npm run migrate:up` | Apply pending migrations (uses `DATABASE_URL`) |
| `npm run migrate:down` | Roll back the most recent migration |
| `npm run migrate:create -- <name>` | Scaffold a new SQL migration file |

## Folder structure

```
src/
  index.ts          — Express app + server entry
  routes/           — Route handlers (auth, stations, checkins)
  middleware/       — Auth (Firebase JWT) + structured error handler
  models/           — TypeScript interfaces for domain objects
  db/               — pg Pool + query helper
db/
  migrations/       — SQL migration files (node-pg-migrate)
Dockerfile         — Multi-stage build for the api container
```

## Database migrations

Migrations live in `db/migrations/` and are managed by [`node-pg-migrate`](https://github.com/salsita/node-pg-migrate). Each `.sql` file contains an `-- Up Migration` section and a matching `-- Down Migration` section, and applied migrations are tracked in the `pgmigrations` ledger table.

```sh
# Apply all pending migrations
npm run migrate:up

# Roll back the most recent
npm run migrate:down

# Create a new migration file
npm run migrate:create -- add_some_column
```

In CI, migrations are applied against the test Postgres service before the test suite runs (see `.github/workflows/backend-ci.yml`).
