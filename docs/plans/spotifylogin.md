# Concept: Spotify Login

## Goal

Implement a working Spotify OAuth 2.0 login that:

- Completes the Authorization Code Flow and obtains a token pair (access + refresh)
- Verifies the authenticated Spotify user against a configured allow list
- Persists user data and tokens in MongoDB only if the user is allow-listed
- Protects all non-login endpoints behind session-based authentication
- Supports multiple allow-listed users without requiring a user-management UI

---

## Spotify OAuth App Setup

### Creating the App (one-time, per environment)

1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click **Create App**
3. Fill in:
   - **App name** – e.g. `spotify-control-local` / `spotify-control-prod`
   - **App description** – optional
   - **Redirect URIs** – add the URIs for every environment you plan to use (see below)
   - **APIs used** – select "Web API"
4. Note the **Client ID** and **Client Secret** – these map to `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET`

### Required Redirect URIs

Register all URIs that will be used. Each environment needs its own entry:

| Environment  | Redirect URI                                          |
|--------------|-------------------------------------------------------|
| Local dev    | `http://localhost:8080/oauth/callback`                |
| Production   | `https://spotify.yourdomain.com/oauth/callback`       |

> **Note:** The path `/oauth/callback` must match the value configured in the application (see Configuration section below). Spotify enforces an exact match including protocol and port.

### Required Scopes

The following OAuth scopes must be requested during the authorization flow:

| Scope                          | Purpose                                             |
|--------------------------------|-----------------------------------------------------|
| `user-read-recently-played`    | Fetch playback history (core feature)               |
| `playlist-read-private`        | Read private playlists                              |
| `playlist-modify-public`       | Edit public playlists (album upgrade apply)         |
| `playlist-modify-private`      | Edit private playlists (album upgrade apply)        |
| `user-read-private`            | Read user profile, including Spotify user ID        |

---

## Authorization Code Flow

The application uses the **OAuth 2.0 Authorization Code Flow** as described in the [Spotify documentation](https://developer.spotify.com/documentation/web-api/tutorials/code-flow).

### Flow Overview

```
Browser                       adapter-in-web              Spotify API
   |                               |                           |
   |--- GET /                      |                           |
   |<-- 302 /login (no session)    |                           |
   |--- GET /login                 |                           |
   |<-- login page (login button)  |                           |
   |                               |                           |
   |--- click "Log in with Spotify"|                           |
   |--- GET /oauth/authorize ------>|                           |
   |<-- 302 accounts.spotify.com   |                           |
   |--- GET accounts.spotify.com ----------------------------->|
   |<-- Spotify login + consent <----------------------------- |
   |--- user grants consent ---------------------------------->|
   |<-- 302 /oauth/callback?code=... <------------------------ |
   |--- GET /oauth/callback ------->|                           |
   |                                |--- POST /api/token ------>|
   |                                |<-- access+refresh token --|
   |                                |--- GET /v1/me ----------->|
   |                                |<-- user profile ----------|
   |                                |                           |
   |                                |-- check allow list        |
   |                                |-- (denied: clear session) |
   |                                |-- (allowed: persist user) |
   |<-- 302 /ui/dashboard ---------|                           |
```

### Steps in Detail

1. **Unauthenticated request** – Any request without a valid session redirects to `/login`.
2. **Login page** – The login page shows a "Log in with Spotify" button that links to `/oauth/authorize`.
3. **Authorization redirect** – The application redirects the browser to `accounts.spotify.com` with `client_id`, `redirect_uri`, `scope`, `response_type=code`, and a CSRF `state` parameter.
4. **Spotify callback** – Spotify redirects back to `/oauth/callback?code=...&state=...`.
5. **Token exchange** – The callback handler POST-exchanges the `code` for an `access_token` and `refresh_token` via `accounts.spotify.com/api/token`.
6. **User profile fetch** – The handler calls `GET /v1/me` with the new access token to retrieve the Spotify user profile (including `id`).
7. **Allow-list check** – The Spotify user ID is checked against the configured allow list (`APP_ALLOWED_SPOTIFY_USER_IDS`, comma-separated). If not present, the session is cleared and the user is redirected to `/login` with an error indicator.
8. **User persistence** – If allow-listed, a `User` document is upserted into the `users` MongoDB collection (keyed by Spotify user ID). The refresh token is stored encrypted.
9. **Session creation** – A server-side session is established for the authenticated user; the session stores only the Spotify user ID.
10. **Redirect** – The user is redirected to the original request target (from `return_to` in the session) or to `/ui/dashboard`.

### CSRF Protection

A random `state` value is generated per authorization request and stored in the session. The callback handler validates `state` before proceeding. Mismatches abort the flow.

---

## Token Handling

### Storage

Tokens are persisted in the `users` MongoDB collection:

```json
{
  "_id": "<spotify_user_id>",
  "displayName": "Alice",
  "accessToken": "<encrypted>",
  "refreshToken": "<encrypted>",
  "tokenExpiresAt": "<ISO-8601 timestamp>",
  "createdAt": "<ISO-8601 timestamp>",
  "lastLoginAt": "<ISO-8601 timestamp>"
}
```

Both `accessToken` and `refreshToken` are encrypted at rest using `APP_TOKEN_ENCRYPTION_KEY` (AES-256-GCM). The key is never stored in the database or source code.

### Token Refresh

Access tokens expire after 1 hour. The `adapter-out-spotify` module is responsible for token refresh:

- Before every Spotify API call, check if `tokenExpiresAt` is within the next 5 minutes.
- If so (or if a `401` is received), perform a refresh via `POST accounts.spotify.com/api/token` with `grant_type=refresh_token`.
- Persist the new `accessToken` and updated `tokenExpiresAt` back to MongoDB.
- The `refreshToken` is long-lived and only rotated when Spotify issues a new one (it may be included in the refresh response).

### Multi-User Scenario

All active sessions have their tokens stored independently per Spotify user ID. The polling and sync jobs must look up the relevant user's tokens before making API calls. Token refresh is per-user.

---

## Module Responsibilities

| Module              | Responsibility                                                                        |
|---------------------|---------------------------------------------------------------------------------------|
| `adapter-in-web`    | `/oauth/authorize` redirect, `/oauth/callback` handler, session management            |
| `adapter-out-spotify` | Token refresh logic, `GET /v1/me` call                                              |
| `domain-impl`       | Login domain service (orchestrates callback handling)                                 |
| `application-quarkus` | Security configuration, session configuration, route protection                   |

---

## Security Configuration

### Route Protection

All routes except `/login`, `/oauth/authorize`, and `/oauth/callback` require an authenticated session:

```
/                  → redirect to /ui/dashboard (authenticated) or /login (unauthenticated)
/login             → public (PermitAll)
/oauth/authorize   → public (PermitAll)
/oauth/callback    → public (PermitAll)
/ui/**             → authenticated
/api/**            → authenticated
```

### Session Management

- Server-side sessions (Quarkus session/cookie-based)
- Session stores only the Spotify user ID (no tokens in session)
- `return_to` is stored temporarily in the session for post-login redirect
- `state` parameter is stored in session for CSRF validation

---

## Configuration Reference

All configuration is provided via environment variables. No secrets in source code or deployment files.

| Environment Variable          | Description                                                  | Example                          |
|-------------------------------|--------------------------------------------------------------|----------------------------------|
| `SPOTIFY_CLIENT_ID`           | OAuth app Client ID from Spotify Developer Dashboard         | `abc123...`                      |
| `SPOTIFY_CLIENT_SECRET`       | OAuth app Client Secret                                      | `xyz789...`                      |
| `APP_ALLOWED_SPOTIFY_USER_IDS`| Comma-separated list of allowed Spotify user IDs             | `alice123,bob456`                |
| `APP_TOKEN_ENCRYPTION_KEY`    | 256-bit AES key (Base64-encoded) for token encryption at rest| `<base64-32-bytes>`              |
| `MONGODB_CONNECTION_STRING`   | MongoDB Atlas connection string                              | `mongodb+srv://...`              |

### Local Development Setup

1. Create a Spotify Developer App at [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Add `http://localhost:8080/oauth/callback` as a Redirect URI
3. Note Client ID and Client Secret
4. Find your Spotify user ID (see [Finding Your Spotify User ID](#finding-your-spotify-user-id))
5. Generate a random 32-byte AES key: `openssl rand -base64 32`
6. Create a local `.env` file (not committed to Git):

```bash
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
APP_ALLOWED_SPOTIFY_USER_IDS=your_spotify_user_id
APP_TOKEN_ENCRYPTION_KEY=your_base64_key
MONGODB_CONNECTION_STRING=mongodb+srv://...
```

7. Start the application: `./gradlew :application-quarkus:quarkusDev`
8. Open `http://localhost:8080` and click "Log in with Spotify"

---

## Error Handling

| Scenario                              | Behavior                                                                 |
|---------------------------------------|--------------------------------------------------------------------------|
| `state` mismatch in callback          | Abort flow, redirect to `/login?error=state_mismatch`                   |
| Spotify returns error in callback     | Redirect to `/login?error=spotify_denied`                                |
| Token exchange fails                  | Redirect to `/login?error=token_exchange_failed`                         |
| User not in allow list                | Clear session, redirect to `/login?error=not_allowed`                    |
| MongoDB write fails                   | Log error, redirect to `/login?error=internal`                           |
| Token refresh fails (expired/revoked) | Clear session for that user, redirect to `/login` on next request        |

---

## Testing Strategy

| Layer             | Test Type          | What to test                                                                  |
|-------------------|--------------------|-------------------------------------------------------------------------------|
| Domain            | Unit test          | Token expiry logic                                                            |
| OAuth callback    | `@QuarkusTest`     | Happy path (mock Spotify endpoints), state mismatch, user not allowed         |
| Token encryption  | Unit test          | Encrypt → store → retrieve → decrypt round-trip                               |
| Token refresh     | Unit test          | Refresh triggered within 5-minute window, 401 also triggers refresh           |
| Route protection  | `@QuarkusTest`     | Unauthenticated requests redirected to `/login`                               |
