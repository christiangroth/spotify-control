# Initial deployment

- maintain and also serve release notes
- add OpenAPI to adapter-in-web
- add adapter-out-mongodb
- create minimal outbox
- create mongodb atlas instances
- deploy to vps

# Spotify connect

- add adapter-out-spotify
- add spotify login
- add dashboard
- serve docs

# Playback data gathering

- refresh token habdling
- cronjob to fetch recently played
- duplicate habdling
- simple dashboard stats (outbox, playback events)

# Spotify API

- metrics about sporify requests on dashboard
- maybe grafana dashboard
- cronjob to sync artists, releases and tracks
- dashboard stats on synced spotify data

# Playlist connect

- Playlist selection (separate ui)
- cronjob to sync playlists
- throttle outbox for massive syncs / handle HTTP 429
- playlist stats on dashboard

# Metrics / Monitoring

- metrics about MongoDB on dashboard
- maybe grafana dashboard
- Make logs accessible

# Future

- continue with phase three
- add adapter-out-slack
