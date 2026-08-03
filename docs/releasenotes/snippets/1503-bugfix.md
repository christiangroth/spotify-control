* The playlist checks tab now loads much faster, since its dashboard is now precomputed whenever checks run instead of being recomputed on every page view.
* The playlist settings tab and dashboard now load much faster too, since they read a precomputed summary instead of recomputing playlist/artist/playback statistics on every page view.
* The stats page now shows track/album/artist cover images and artist names without an extra lookup on every page view, since they are resolved once when playback statistics are computed instead.
* The playback page now loads faster too, since it reads the same precomputed dashboard summary instead of recomputing playback statistics on every page view.
* Running all playlist checks at once no longer rebuilds the checks dashboard once per playlist, avoiding redundant work when many playlists are checked together.
* Fixed the playlist checks page sometimes showing a blank header instead of falling back to the account name.
