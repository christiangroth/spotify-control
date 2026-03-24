* Fixed partial playback duration sometimes showing impossibly large values (e.g. 42039 seconds) by capping the computed duration at the track's actual length.
* Added one-time startup migration to cap any existing partial playback entries with duration exceeding the track's actual length.
