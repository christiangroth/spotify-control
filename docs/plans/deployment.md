# Deployment Environment Variables

This document describes all environment variables required to deploy spotify-control in production.

## Required Variables

All variables must be provided via a `.env` file on the VPS (never checked into Git) and referenced
from `deploy/docker-stack.yml`.

| Variable                        | Description                                                                                  |
|---------------------------------|----------------------------------------------------------------------------------------------|
| `DOCKER_TAG`                    | Docker image tag to deploy (e.g. a Git tag like `1.0.0`).                                   |
| `QUARKUS_PROFILE`               | Quarkus configuration profile. Must be set to `prod` in production.                         |
| `MONGODB_CONNECTION_STRING`     | Full MongoDB Atlas connection string (including credentials and cluster URL).                |
| `SPOTIFY_CLIENT_ID`             | Spotify Developer App client ID.                                                             |
| `SPOTIFY_CLIENT_SECRET`         | Spotify Developer App client secret.                                                         |
| `APP_OAUTH_REDIRECT_URI`        | Spotify OAuth redirect URI registered in the Spotify Developer App (production URL).         |
| `APP_ALLOWED_SPOTIFY_USER_IDS`  | Comma-separated list of Spotify user IDs allowed to log in.                                  |
| `APP_TOKEN_ENCRYPTION_KEY`      | Base64-encoded 32-byte AES-256 key used to encrypt stored Spotify access and refresh tokens. |
| `HTTP_AUTH_ENCRYPTION_KEY`      | Base64-encoded 32-byte AES-256 key used to encrypt the session cookie value.                 |
| `TRAEFIK_HTTP_ROUTERS_SPOTIFYCONTROL_RULE` | Traefik routing rule (e.g. `` Host(`spotify.yourdomain.com`) ``).           |

## Traefik

The stack assumes an externally managed Traefik instance connected via the `global_router` Docker
network. TLS certificates are obtained automatically via Let's Encrypt (`certresolver=le`).

## Key Generation

Generate the encryption keys with:

```bash
openssl rand -base64 32
```

Run this command twice to generate `APP_TOKEN_ENCRYPTION_KEY` and `HTTP_AUTH_ENCRYPTION_KEY`
independently.

## Spotify OAuth Redirect URIs

Register both URIs in the Spotify Developer App (replace `spotify.yourdomain.com` with your domain):

```
https://spotify.yourdomain.com/oauth/callback   ← Production
http://localhost:8080/oauth/callback             ← Local development
```
