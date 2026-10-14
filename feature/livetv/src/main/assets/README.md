# Live TV sample clip

`al_fatiha.mp4` (~3 MB) in this folder is the mock "live" broadcast. The Live TV channel
loops it, and every radio station plays its audio, until the real streaming backend exists.

The file is **gitignored**: it is a third-party recitation video, so it is not pushed to
GitHub. Copy it here locally before building, otherwise the Live TV screen shows its
"stream unavailable" state.

To change the clip, replace the file and update `LocalLiveChannelRepository.SAMPLE_ASSET`,
or switch `LocalLiveChannelRepository` and `LocalRadioStationRepository` to remote sources.
