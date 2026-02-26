# Side quests

- extract template repository
- Code and architecture review
- Update coding guidelines
- Dependency updates and other maintenance tasks
- local development?

# Spotify Connect

- Define Spotify OAuth App
- Add missing deployment params as GitHub Actions secrets

# Playback data gathering

- create minimal outbox
- cronjob to fetch recently played
- cronjob to update user profiles
- playback duplicate/skip handling
- simple dashboard stats (outbox, playback events)

# Spotify API

- metrics about Spotify requests on dashboard
- maybe Grafana dashboard
- cronjob to sync artists, releases and tracks
- dashboard stats on synced Spotify data

# Playlist connect

- Playlist selection (separate ui)
- cronjob to sync playlists
- throttle outbox for massive syncs / handle HTTP 429
- playlist stats on dashboard

# Metrics / Monitoring

- metrics about MongoDB on dashboard
- maybe Grafana dashboard
- Make logs accessible

# Future

- continue with phase three
- add adapter-out-slack
