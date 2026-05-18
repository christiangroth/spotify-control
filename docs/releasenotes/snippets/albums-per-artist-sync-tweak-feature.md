* Spotify artist album sync now includes singles in addition to albums.
* Catalog outbox split into separate artist and album partitions with dedicated rate limits.
* Artist catalog requests are throttled at 60 seconds; album catalog requests use the default 10 second interval.
