* Added outbox status indicator in the navbar: green when all outbox partitions are active, red when any are paused or rate-limited. Hover to view full outbox partition details.
* Added playback status indicator in the navbar: green when playback is active, grey when no playback is detected.
* Both health indicators are hidden on the login page and kept up to date via SSE on all other pages.
* On the health page, state indicators and cronjobs are now shown side by side in one row.
* State indicators now show a "Since" column with the last check timestamp, use a grey icon for inactive state, and display the status icon before the predicate name.
