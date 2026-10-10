# Berkeley Google Maps, Places, & Routes Setup Guide

This guide documents the setup requirements for live mapping, destination autocomplete, walking route planning, and public safety data in Guardian Angel.

---

## 1. Required Google Cloud APIs

To enable live Google Maps and routing, enable the following APIs in your Google Cloud Console project:
1. **Maps SDK for Android** (`maps-android-backend.googleapis.com`)
   - Powers the Google Map view, camera animations, and polyline rendering.
2. **Places API (New)** (`places.googleapis.com`)
   - Powers live destination search, address autocomplete with Berkeley-area biasing, and place details.
3. **Routes API v2** (`routes.googleapis.com`)
   - Powers walking route computation, alternative paths, and distance/duration estimates.

> [!NOTE]
> Google Cloud requires an active billing account linked to the project for Maps and Places APIs, even though monthly free tier credits are provided.

---

## 2. API Key Configuration & Secrets Management

To ensure security and prevent committing secrets to source control, API keys are loaded locally from `secrets.properties` or `local.properties`.

### Where to Place Your Key
Create a `secrets.properties` file in the project root directory (or add to `local.properties`):

```properties
MAPS_API_KEY=AIzaSyYourActualGoogleMapsApiKeyHere
```

Both `secrets.properties` and `local.properties` are in `.gitignore` and are never committed.

### Missing Key & Offline Behavior
When `MAPS_API_KEY` is not provided or remains the placeholder (`AIzaSyPlaceholderGuardianAngelKey`):
- The app compiles and runs cleanly without crashing.
- Destination search gracefully falls back to the curated Berkeley civic and campus landmarks catalog (Doe Library, Sproul Plaza, Downtown BART, Trader Joe's, BPD HQ).
- Route planning falls back to verified pedestrian corridors across Downtown, Telegraph, and Campus with realistic distance and timing.
- Deterministic safety scoring evaluates the corridors using official Berkeley public safety cells.

---

## 3. Recommended API Key Restrictions

For Android prototype and production security:

### Application Restrictions
Restrict your API key to the Android app using package name and SHA-1 signing certificate fingerprint:
- **Package Name**: `com.example.guardianangel`
- **Debug SHA-1 Fingerprint**: Obtain using:
  ```bash
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```

### API Restrictions
Restrict the key strictly to:
- Maps SDK for Android
- Places API (New)
- Routes API

> [!IMPORTANT]
> **Production Proxy Guidance**: For production deployment, Google recommends proxying Routes API and Places calls through a secure backend server with App Check or OAuth tokens rather than invoking billing APIs directly from client devices. The architecture in `RouteProvider` and `GoogleRoutesApiProvider` is designed to swap to a backend proxy with zero UI changes.

---

## 4. Location Permissions & Android Platform Rules

### Runtime Permissions
Guardian Angel declares and requests:
- `android.permission.ACCESS_FINE_LOCATION` (GPS high accuracy)
- `android.permission.ACCESS_COARSE_LOCATION` (Network cell/Wi-Fi)

### Foreground Service Invariants
- Continuous hands-free listening and background location tracking are governed by Android 14+ foreground service types (`microphone` and `location`).
- Android **prohibits starting foreground services from the background** (`ForegroundServiceStartNotAllowedException`).
- Tracking and arming must be initiated from a visible app screen by explicit user action ("Arm before you set off").
- When location permission is denied: the app displays a calm permission banner, never fabricates fake GPS coordinates, and allows map exploration and search.

---

## 5. Berkeley Public Safety Data & Coverage Area

### Supported Geographic Boundary
- **Latitude**: $37.830^\circ\text{ N}$ to $37.910^\circ\text{ N}$
- **Longitude**: $-122.330^\circ\text{ W}$ to $-122.230^\circ\text{ W}$
- Covers Downtown Berkeley, UC Berkeley Campus (Southside, Northside, Central), Shattuck Ave, Telegraph Ave, University Ave, San Pablo Ave, and Ashby BART.

### Jurisdiction Separation
- **City of Berkeley Police Department (BPD)**: Municipal public safety calls.
- **UC Berkeley Police Department (UCPD)**: University property and Clery Act logs.
- Maintained as distinct agencies without cross-jurisdiction assumptions.

### Privacy Invariants
- Street incidents are aggregated into **150-meter spatial cells**.
- Individual victim names, exact private apartment numbers, and raw police incident numbers are never shown on maps.
- Clusters with fewer than 2 incidents are suppressed.
- Outside verified Berkeley coverage, the system reports **"Limited safety data"** rather than assuming safety.
