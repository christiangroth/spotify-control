* Fixed a slow `outbox.task.findByPartition` query on the Outbox Viewer page by caching partition task lists for 15 seconds instead of re-querying on every page load or partition-change notification.
