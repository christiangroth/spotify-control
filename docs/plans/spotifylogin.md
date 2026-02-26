# Concept: Spotify Login – Remaining Work

## Token Refresh

Access tokens expire after 1 hour. The `adapter-out-spotify` module needs to handle token refresh:

- Before every Spotify API call, check if `tokenExpiresAt` is within the next 5 minutes.
- If so (or if a `401` is received), perform a refresh via `POST accounts.spotify.com/api/token` with `grant_type=refresh_token`.
- Persist the new `accessToken` and updated `tokenExpiresAt` back to MongoDB.
- The `refreshToken` is long-lived and only rotated when Spotify issues a new one (it may be included in the refresh response).
- On refresh failure (token revoked), clear the session and redirect the user to `/login`.

---

## Finding Your Spotify User ID

1. Open [open.spotify.com](https://open.spotify.com) in a browser
2. Click your profile icon → **Profile**
3. The URL contains your user ID: `https://open.spotify.com/user/<your-user-id>`
