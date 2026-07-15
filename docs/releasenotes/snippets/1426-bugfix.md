* Moved playlist, catalog, outbox, MongoDB collection, and application info metrics into the dedicated metrics module, keeping the metrics caching separate from domain logic.
* MongoDB collection size stats are now cached and shared, so the health page/live updates no longer trigger a separate MongoDB query for each read.
