# TelePlay Release Versioning

Current baseline release: **1.10**.

Until the owner explicitly requests a new major/minor version, Android maintenance releases must use the following format:

- `1.10.01`
- `1.10.02`
- …
- `1.10.10`

The corresponding Git tags use the `v` prefix, for example `v1.10.01`.

Do not bump to `1.11` or another minor version without explicit owner instruction.

Each patch release increments the Android `versionCode` by one.
