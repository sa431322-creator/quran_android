# Live TV sample clips

The videos in this folder are the mock "live" broadcasts, looped by `LivePlayerController`
until the real streaming backend exists:

- `sample_stream.mp4` (~389 MB) — every channel.
- `second_stream.mp4` (~1.09 GB) — played after `sample_stream.mp4` on the main channel.

Both files are **gitignored** (GitHub rejects files over 100 MB). Copy them here locally
before building, otherwise the Live TV screen shows its "stream unavailable" state.

**Before any Play release**, replace them with short, compressed clips (~10–20 MB each),
or switch `LocalLiveChannelRepository` to a remote HLS/DASH source.
