# Live TV sample clip

`sample_stream.mp4` in this folder is the mock "live" broadcast. It is played on a loop
by `LivePlayerController` until the real streaming backend exists.

- The file is **gitignored** (it is ~389 MB; GitHub rejects files over 100 MB).
  Copy it here locally before building, otherwise the Live TV screen shows its
  "stream unavailable" state.
- **Before any Play release**, replace it with a short, compressed clip (~10–20 MB),
  or switch `LocalLiveChannelRepository` to a remote HLS/DASH source.
