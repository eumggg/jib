# JIB — EV Charging Station Finder

Monorepo root for the JIB Android app and backend API.

## Structure

```
android/    — Kotlin + Jetpack Compose Android app
backend/    — Node.js + TypeScript REST API
.github/    — CI/CD workflows
docker-compose.yml — Local dev: PostgreSQL + PostGIS
```

## Quick start

### 1. Database

```sh
docker compose up -d db
# PostGIS available at localhost:5432
# user: jib  password: jib_dev  database: jib
```

### 2. Backend

```sh
cd backend
cp .env.example .env
# edit .env with your Firebase project ID
npm install
npm run dev
```

### 3. Android

```sh
cd android
cp local.properties.example local.properties
# edit local.properties with your Maps API key
# place google-services.json in android/app/
./gradlew assembleDebug
```

## CI

- `android-ci.yml` — lint + assembleDebug on Android changes
- `backend-ci.yml` — lint + test + tsc build on backend changes

Both trigger on push to `main`/`develop` and on pull requests.

## Secrets (never commit)

| Secret | Where used |
|--------|-----------|
| `android/local.properties` | Android build config |
| `android/app/google-services.json` | Firebase Android config |
| `backend/.env` | Backend runtime config |
| Firebase service account JSON | Backend Firebase Admin SDK |
