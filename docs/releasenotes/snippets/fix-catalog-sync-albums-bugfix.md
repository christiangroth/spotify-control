* Catalog sync no longer hammers all album pages in a single burst for artists with many albums.
* Each page of the artist albums sync is now processed as a separate outbox task, respecting rate limit delays.
