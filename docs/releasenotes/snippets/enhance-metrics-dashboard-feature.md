* enhance-metrics-dashboard: Removed JVM Classes, Spotify API Tracked Hosts, and Starter Execution Duration panels from the metrics dashboard.
* enhance-metrics-dashboard: Incoming HTTP requests panel no longer shows redirect responses.
* enhance-metrics-dashboard: Heap and non-heap memory "max" series are now hidden by default in the metrics dashboard.
* enhance-metrics-dashboard: Spotify API request URLs are now grouped by URL pattern, so requests to the same endpoint with different IDs are aggregated together.
* enhance-metrics-dashboard: Failed and rate-limited task rate panels now display 0 instead of showing no data when no failures have occurred.
* enhance-metrics-dashboard: Partition status panel now always shows the last known state, even when no data was recorded in the selected time range.
* enhance-metrics-dashboard: Task enqueue and task processed rates are now shown in separate panels.
