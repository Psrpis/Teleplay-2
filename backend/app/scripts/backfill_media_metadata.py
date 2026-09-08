"""Backfill duration and video dimensions for media rows missing metadata."""
from __future__ import annotations

import argparse
import asyncio
import json
import logging
import tempfile
from pathlib import Path

from sqlalchemy import func, select

from .. import telegram
from ..database import async_session
from ..models import File

logger = logging.getLogger("teleplay.backfill_media_metadata")


async def probe_media(path: Path) -> dict:
    process = await asyncio.create_subprocess_exec(
        "ffprobe",
        "-v", "quiet",
        "-print_format", "json",
        "-show_format",
        "-show_streams",
        str(path),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    stdout, stderr = await process.communicate()
    if process.returncode != 0:
        detail = stderr.decode(errors="replace").strip()
        raise RuntimeError(detail or f"ffprobe exited with {process.returncode}")
    return json.loads(stdout)


def metadata_from_probe(data: dict, file_type: str) -> tuple[int | None, int | None, int | None]:
    format_info = data.get("format") or {}
    duration_value = format_info.get("duration")
    if duration_value is None:
        for stream in data.get("streams") or []:
            if stream.get("duration") is not None:
                duration_value = stream["duration"]
                break
    duration = max(0, int(round(float(duration_value)))) if duration_value is not None else None
    if file_type != "video":
        return duration, None, None
    video_stream = next((stream for stream in data.get("streams") or [] if stream.get("codec_type") == "video"), {})
    return duration, video_stream.get("width"), video_stream.get("height")


async def backfill(limit: int | None = None, delay: float = 0.5) -> int:
    async with async_session() as db:
        total_query = select(func.count(File.id)).where(
            File.file_type.in_(("video", "audio")),
            File.duration.is_(None),
        )
        total = int((await db.execute(total_query)).scalar_one())
        query = select(File).where(
            File.file_type.in_(("video", "audio")),
            File.duration.is_(None),
        ).order_by(File.id)
        if limit:
            query = query.limit(limit)
        files = (await db.execute(query)).scalars().all()

    if not files:
        logger.info("No media rows need metadata backfill")
        return 0

    telegram.build_clients()
    await telegram.start_all_clients()
    processed = 0
    try:
        for file in files:
            temp_path: Path | None = None
            try:
                suffix = Path(file.file_name or "media.bin").suffix or ".bin"
                with tempfile.NamedTemporaryFile(prefix="teleplay-", suffix=suffix, delete=False) as handle:
                    temp_path = Path(handle.name)
                message = await telegram.get_message_from_channel(file.channel_message_id)
                await telegram.tg_client.download_media(message, file_name=str(temp_path))
                probe = await probe_media(temp_path)
                duration, width, height = metadata_from_probe(probe, file.file_type)
                if duration is None:
                    raise RuntimeError("ffprobe returned no duration")

                async with async_session() as db:
                    current = await db.get(File, file.id)
                    if current and current.duration is None:
                        current.duration = duration
                        if current.file_type == "video":
                            current.width = width
                            current.height = height
                        await db.commit()
                processed += 1
                logger.info("Processed %d/%d (file id %s)", processed, total, file.id)
            except Exception:
                logger.exception("Failed to process file id %s (%s)", file.id, file.file_name)
            finally:
                if temp_path:
                    temp_path.unlink(missing_ok=True)
            if delay:
                await asyncio.sleep(delay)
    finally:
        await telegram.stop_all_clients()
    return processed


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=None, help="Process at most this many media rows")
    parser.add_argument("--delay", type=float, default=0.5, help="Seconds to wait between files")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    await backfill(args.limit, max(0, args.delay))


if __name__ == "__main__":
    asyncio.run(main())
