* Catalog sync now fetches up to 50 albums per page instead of 10, reducing Spotify API request count by up to 5x.
* Catalog sync stops fetching additional album pages for an artist when all albums on the current page are already known, avoiding unnecessary Spotify API calls during re-syncs.
