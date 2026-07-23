* Disabled the outbox archive: completed and failed outbox tasks are no longer kept for auditing, and any previously archived entries are cleared automatically.
* Updated the outbox library dependency to 0.8.4, which now correctly stops archiving when disabled instead of only skipping the retention cleanup.
