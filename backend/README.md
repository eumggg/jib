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
5. Build:
   ```sh
   npm run build
   ```
6. Start dev server with hot-reload:
   ```sh
   npm run dev
   ```

## Scripts

| Command | Description |
|---------|-------------|
| `npm run build` | Compile TypeScript → `dist/` |
| `npm start` | Run compiled output |
| `npm run dev` | Development server with hot-reload |
| `npm run lint` | ESLint check |
| `npm test` | Jest unit tests |

## Folder structure

```
src/
  index.ts          — Express app + server entry
  routes/           — Route handlers (stations, checkins)
  middleware/       — Auth (Firebase JWT verification)
  models/           — TypeScript interfaces for domain objects
  db/               — pg Pool + query helper
db/
  migrations/       — SQL migration scripts (run on docker init)
```

## Database migrations

SQL scripts in `db/migrations/` are auto-run by docker-compose on first container start via `docker-entrypoint-initdb.d`. For subsequent migrations, run them manually:

```sh
psql $DATABASE_URL -f db/migrations/002_next_migration.sql
```
