* Fixed the hourly playlist sync doing one MongoDB round-trip per playlist just to check for missing data; it now does a single batched lookup instead.
