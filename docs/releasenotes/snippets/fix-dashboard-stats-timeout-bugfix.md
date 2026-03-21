* Fixed dashboard stats timeouts by splitting queries into focused per-section operations.
* Each dashboard section now only runs the queries it needs instead of loading all stats at once.
* Added MongoDB compound index on playback data to speed up listening stats aggregation.
