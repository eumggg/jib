# JIB Android App

EV charging station finder — Kotlin + Jetpack Compose.

## Prerequisites

- JDK 17+
- Android SDK (via Android Studio or `sdkmanager`)
- Google Maps API key
- Firebase project (Auth, FCM, Storage enabled)

## Setup

1. Copy the example local properties file:
   ```sh
   cp local.properties.example local.properties
   ```
2. Fill in `local.properties`:
   - `MAPS_API_KEY` — get from [Google Cloud Console](https://console.cloud.google.com) → Maps SDK for Android
   - `API_BASE_URL` — backend URL (`http://10.0.2.2:3000` works for Android emulator against local backend)
   - `sdk.dir` — path to your Android SDK (Android Studio sets this automatically)
3. Place your `google-services.json` in `app/` (download from Firebase Console).
4. Build debug APK:
   ```sh
   ./gradlew assembleDebug
   ```

## Run

Open in Android Studio and run on an emulator or physical device.

## Architecture

- Single-module Kotlin app with Jetpack Compose UI
- Hilt for dependency injection
- Room for local station cache (offline-first)
- Retrofit + OkHttp for REST API calls
- Firebase Auth for JWT-based authentication
- Google Maps SDK + Maps Compose for map view

## Lint

```sh
./gradlew lint
```
