# Deployment Konzept

## Infrastruktur

- **VPS** mit Docker Swarm (bereits vorhanden)
- **Traefik** übernimmt Routing, TLS, HTTPS (bereits vorhanden)
- **MongoDB** extern via Atlas (kein lokaler MongoDB-Container)
- **Quarkus** als native Docker Container im Swarm

## Docker Image

Quarkus native bauen

## Docker Swarm Stack (work in progress)

```yaml
version: '3.8'

services:
  app:
    image: spotifymanager:latest
    networks:
      - traefik-public
    environment:
      APP_ALLOWED_SPOTIFY_USER_ID: ${APP_ALLOWED_SPOTIFY_USER_ID}
      SPOTIFY_CLIENT_ID: ${SPOTIFY_CLIENT_ID}
      SPOTIFY_CLIENT_SECRET: ${SPOTIFY_CLIENT_SECRET}
      MONGODB_CONNECTION_STRING: ${MONGODB_CONNECTION_STRING}
      APP_TOKEN_ENCRYPTION_KEY: ${APP_TOKEN_ENCRYPTION_KEY}
      SLACK_WEBHOOK_URL: ${SLACK_WEBHOOK_URL}
    deploy:
      replicas: 1
      restart_policy:
        condition: on-failure
      labels:
        - "traefik.enable=true"
        - "traefik.http.routers.spotifymanager.rule=Host(`spotify.yourdomain.com`)"
        - "traefik.http.routers.spotifymanager.entrypoints=websecure"
        - "traefik.http.routers.spotifymanager.tls.certresolver=letsencrypt"
        - "traefik.http.services.spotifymanager.loadbalancer.server.port=8080"

networks:
  traefik-public:
    external: true
```

Secrets nie in der Stack-Datei – immer via Umgebungsvariablen aus einer `.env`-Datei die nicht in Git liegt.

## Zugriffsbeschränkung

**Quarkus Spotify-User-ID-Check** (Applikationsebene) – im OAuth-Callback via `APP_ALLOWED_SPOTIFY_USER_ID`

## Spotify OAuth Redirect URI

In der Spotify Developer App müssen beide URIs registriert sein:

```
https://spotify.yourdomain.com/oauth/callback   ← Produktion
http://localhost:8080/oauth/callback             ← Lokale Entwicklung
```

## Lokale Entwicklung vs. Produktion

|                  | Lokal                         | Produktion               |
|------------------|-------------------------------|--------------------------|
| MongoDB          | Atlas Dev-Cluster             | Atlas Prod-Cluster       |
| Quarkus Profil   | `dev`                         | `prod`                   |
| Spotify Redirect | `localhost:8080`              | `spotify.yourdomain.com` |
| Container        | nein (direkter Quarkus-Start) | Docker Swarm             |

Quarkus-Profil wird via Umgebungsvariable gesteuert:

```bash
QUARKUS_PROFILE=prod
```

## Deployment-Workflow (work in progress)

```bash
# Build
./gradlew build -Dquarkus.package.type=fast-jar

# Image bauen und taggen
docker build -t spotifymanager:latest .
docker save spotifymanager:latest | gzip > spotifymanager.tar.gz

# Auf VPS übertragen und deployen
scp spotifymanager.tar.gz user@vps:~/
ssh user@vps "docker load < spotifymanager.tar.gz && docker stack deploy -c stack.yml spotifymanager"
```

Alternativ via Docker Registry (GitHub Container Registry o.ä.) für saubereren CI/CD-Flow – für Phase 1 ist manuelles Deploy ausreichend.
