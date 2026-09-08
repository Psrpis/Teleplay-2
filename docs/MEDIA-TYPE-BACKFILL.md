# Media Type and Metadata Backfill

TelePlay classifies Telegram documents by MIME type and filename extension. This preserves playback for videos and audio files sent with `force_document=True`, which avoids Telegram client-side re-encoding but does not always provide Telegram video attributes.

## New uploads

New document uploads are classified as `video` or `audio` when their MIME type or filename extension identifies playable media. Other documents remain `document`.

## Existing library

Reclassify existing document rows from the backend container:

```bash
docker compose exec backend python -m app.scripts.backfill_file_types
```

The command only changes rows currently marked `document`, is safe to repeat, and supports `--limit` for a staged rollout:

```bash
docker compose exec backend python -m app.scripts.backfill_file_types --limit 100
```

After reclassification, install/build the backend image so it includes `ffmpeg` and `ffprobe`, then backfill missing duration and video dimensions:

```bash
docker compose build backend
docker compose up -d backend
docker compose exec backend python -m app.scripts.backfill_media_metadata
```

The metadata job downloads and probes one Telegram file at a time, writes the result immediately, deletes its temporary file, logs progress, and skips rows that already have a duration. It is safe to interrupt and rerun. Use `--limit` and `--delay` to control load on a small server:

```bash
docker compose exec backend python -m app.scripts.backfill_media_metadata --limit 50 --delay 1
```

The metadata job requires the backend Telegram session and credentials to be available. It reads the original forwarded message from the configured storage channel and uses the existing Pyrogram client pool for downloads.
