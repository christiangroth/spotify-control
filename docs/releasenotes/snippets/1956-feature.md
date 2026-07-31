* Playlist check Slack notifications now show the playlist name and violation count instead of the raw ID and full violation list, and now also fire when new violations are found on the first check or when the violation count changes (with the delta).
* New Slack notification when the Spotify login becomes invalid and needs to be renewed.
* New Slack notification when a playlist sync fails.
* New Slack notification for a weekly listening stats digest (minutes played, top artist, top track).
* New Slack notification when an outbox task permanently fails after exhausting all retries.
* Outbox partition status at startup is now reported as a single compact Slack message (e.g. `4/5 outbox partitions active (to-spotify-playback: paused for 2h)`) instead of one message per partition, and the partition-paused notification now includes the date until which it is paused.
