# Live TV sample clip

`second_stream.mp4` (~1.09 GB) in this folder is the mock "live" broadcast. Every Live TV
channel loops it, and every radio station plays its audio, until the real streaming
backend exists.

The file is **gitignored** (GitHub rejects files over 100 MB). Copy it here locally
before building, otherwise the Live TV screen shows its "stream unavailable" state.

**Before any Play release**, replace it with a short, compressed clip (~10–20 MB),
or switch `LocalLiveChannelRepository` and `LocalRadioStationRepository` to remote sources.
