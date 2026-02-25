# Initial deployment

- deploy to vps
  - configure subdomain/DNS
  - adapt SSH host
- extract template repository

# Spotify connect

- add adapter-out-spotify
- add Spotify login
- review
- maintenance tasks

# Playback data gathering

- add adapter-out-mongodb
- create minimal outbox
- create mongodb atlas instances
- refresh token handling
- cronjob to fetch recently played
- duplicate handling
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
