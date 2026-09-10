"""
Shared business logic and database queries.
"""
import asyncio
import json
import logging
import re
import tempfile
from pathlib import Path
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, desc, func, or_
from sqlalchemy.orm import selectinload

from .models import File, WatchProgress, Folder, FileTag, Tag, MediaMetadata
from .auth import create_media_token
from .database import async_session
from . import telegram


logger = logging.getLogger(__name__)


VIDEO_EXTENSIONS = (".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".ts", ".wmv", ".flv")
AUDIO_EXTENSIONS = (".mp3", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".aac")


def classify_media_type(mime_type: str | None, file_name: str | None) -> str:
    """Classify Telegram media, including videos intentionally sent as documents."""
    mime = (mime_type or "").lower()
    name = (file_name or "").lower()
    if mime.startswith("video/") or name.endswith(VIDEO_EXTENSIONS):
        return "video"
    if mime.startswith("audio/") or name.endswith(AUDIO_EXTENSIONS):
        return "audio"
    return "document"


def file_load_options():
    """Reusable eager-load graph for API responses in async SQLAlchemy."""
    return (
        selectinload(File.watch_progress),
        selectinload(File.favorite_links),
        selectinload(File.media_metadata),
        selectinload(File.tag_links).selectinload(FileTag.tag),
    )

def escape_like(value: str) -> str:
    """Escape special LIKE/ILIKE characters to prevent SQL injection."""
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def parse_episode_reference(file_name: str) -> Optional[dict]:
    """Detect common S01E01 and 1x01 episode markers without changing filenames."""
    patterns = (
        r"(?i)[. _-]+s(\d{1,2})e(\d{1,3})(?:[^0-9]|$)",
        r"(?i)[. _-]+(\d{1,2})x(\d{1,3})(?:[^0-9]|$)",
    )
    for pattern in patterns:
        match = re.search(pattern, f" {file_name} ")
        if match:
            marker = match.group(0).strip(" ._-")
            title = re.sub(r"[. _-]+" + re.escape(marker) + r".*$", "", file_name, flags=re.IGNORECASE).strip(" ._-")
            return {"title": re.sub(r"[._]+", " ", title).strip(), "season": int(match.group(1)), "episode": int(match.group(2))}
    return None


def parse_filename_facets(file_name: str) -> dict:
    """Extract searchable series, episode, actor, quality, and codec facets.

    Filenames are treated as hints only.  The original filename and Telegram
    object remain the source of truth, while normalized facets become tags.
    Supports names such as ``Series.S01E02.Actor.One.And.Actor.Two.1080p.HEVC``.
    """
    stem = re.sub(r"\.[^.]+$", "", file_name or "")
    episode = parse_episode_reference(stem)
    normalized = re.sub(r"[._]+", " ", stem)
    normalized = re.sub(r"\s+", " ", normalized).strip(" -")
    technical = re.compile(
        r"(?i)\b(?:2160p|1080p|1440p|720p|480p|4k|8k|x264|x265|h264|h265|hevc|avc|web[- ]?dl|webrip|bluray|brrip|hdr10?|aac|dts|truehd|proper|repack|remux|prt)\b"
    )
    technical_match = technical.search(normalized)
    content = normalized[:technical_match.start()].strip(" ._-") if technical_match else normalized
    date_match = re.search(r"\b\d{1,2}[. _-]\d{1,2}[. _-](?:19|20)?\d{2}\b", content)
    date_content = content
    after_date = ""
    if date_match:
        date_content = content[:date_match.start()].strip(" ._-")
        after_date = content[date_match.end():].strip(" ._-")
    actor_match = re.search(r"(?i)(?:oyuncu|\bcast\b|\bstarring\b|\bbaşrol\b)\s*[:=-]?\s*(.+)$", content)
    actors: list[str] = []
    if actor_match:
        actor_text = re.sub(r"(?i)oyuncu", " ", actor_match.group(0))
        content = content[:actor_match.start()].strip(" .-_")
        actors = [
            re.sub(r"\s+", " ", item).strip(" .-_")
            for item in re.split(r"(?i)\s+(?:and|ve)\s+|\s*&\s*|[,;/]+", actor_text)
            if len(item.strip(" ._-")) >= 1
        ]
    elif date_match and after_date:
        after_words = after_date.split()
        if len(after_words) > 3:
            actors = [" ".join(after_words[:2])]
        content = date_content
    if episode:
        series_title = episode["title"]
        content = re.sub(r"(?i)[. _-]*s\d{1,2}e\d{1,3}.*$|[. _-]*\d{1,2}x\d{1,3}.*$", "", content).strip(" .-_")
        series_title = re.sub(r"[._]+", " ", content or series_title).strip()
    else:
        series_title = re.sub(r"\s+", " ", content).strip()
    quality = (technical_match.group(0).lower().replace(" ", "") if technical_match else None)
    codecs = re.findall(r"(?i)\b(?:x264|x265|h264|h265|hevc|avc)\b", stem)
    facets = {
        "series": series_title,
        "actors": actors,
        "season": episode["season"] if episode else None,
        "episode": episode["episode"] if episode else None,
        "quality": quality,
        "codec": codecs[0].lower() if codecs else None,
    }
    return facets


async def auto_tag_file(db: AsyncSession, file: File) -> dict:
    """Create/reuse normalized tags and metadata for one file, idempotently."""
    facets = parse_filename_facets(file.file_name)
    tag_names: list[str] = []
    if facets["series"]:
        tag_names.append(f"series:{facets['series']}")
    tag_names.extend(f"actor:{actor}" for actor in facets["actors"])
    if facets["quality"]:
        tag_names.append(f"quality:{facets['quality']}")
    if facets["codec"]:
        tag_names.append(f"codec:{facets['codec']}")
    old_links = await db.execute(
        select(FileTag, Tag)
        .join(FileTag.tag)
        .where(
            FileTag.file_id == file.id,
            or_(
                Tag.name.like("series:%"),
                Tag.name.like("actor:%"),
                Tag.name.like("quality:%"),
                Tag.name.like("codec:%"),
            ),
        )
    )
    desired_names = set(tag_names)
    for link, tag in old_links.all():
        if tag.name not in desired_names:
            await db.delete(link)
    existing_tags = (await db.execute(select(Tag).where(Tag.user_id == file.user_id, Tag.name.in_(tag_names)))).scalars().all() if tag_names else []
    by_name = {tag.name: tag for tag in existing_tags}
    for name in tag_names:
        tag = by_name.get(name)
        if not tag:
            tag = Tag(user_id=file.user_id, name=name)
            db.add(tag)
            await db.flush()
        link_exists = await db.execute(select(FileTag).where(FileTag.file_id == file.id, FileTag.tag_id == tag.id))
        if not link_exists.scalar_one_or_none():
            db.add(FileTag(file_id=file.id, tag_id=tag.id))
    metadata = file.__dict__.get("media_metadata")
    if metadata is None:
        metadata = (await db.execute(select(MediaMetadata).where(MediaMetadata.file_id == file.id))).scalar_one_or_none()
    if facets["series"] and not metadata:
        metadata = MediaMetadata(file_id=file.id, title=facets["series"], media_type="series" if facets["season"] else "movie", season=facets["season"], episode=facets["episode"])
        db.add(metadata)
    elif metadata:
        metadata.title = facets["series"]
        metadata.media_type = "series" if facets["season"] else "movie"
        metadata.season = facets["season"]
        metadata.episode = facets["episode"]
    return {**facets, "tags": tag_names}

def sanitize_filename(name: str) -> str:
    """
    Sanitize filename to prevent path traversal and XSS attacks.
    """
    if not name:
        return "unnamed_file"
    
    # Remove null bytes and path separators
    name = name.replace("\x00", "").replace("/", "_").replace("\\", "_")
    
    # Remove dangerous characters
    name = re.sub(r'[<>:"|?*\x00-\x1f]', '_', name)
    
    # Remove leading/trailing dots and spaces
    name = name.strip(". ")
    
    # Limit length
    if len(name) > 255:
        if "." in name:
            ext = name.rsplit(".", 1)[-1][:10]
            name = name[:255 - len(ext) - 1] + "." + ext
        else:
            name = name[:255]
    
    return name if name else "unnamed_file"

def add_urls_to_file(file: File) -> dict:
    """Add stream and thumbnail URLs to file response."""
    progress = file.watch_progress[0] if file.watch_progress else None
    metadata = file.media_metadata
    tags = [link.tag.name for link in file.tag_links if link.tag] if file.tag_links else []
    last_pos = progress.position if progress else 0
    progress_duration = (progress.duration if progress else None) or file.duration
    progress_percent = round(min(100, (last_pos / progress_duration) * 100), 1) if progress_duration and last_pos else 0
    watched_state = "watched" if progress and progress.completed else ("in_progress" if progress_percent > 0 else "unwatched")
    media_token = create_media_token(file.user_id, file.id)
    data = {
        "id": file.id,
        "user_id": file.user_id,
        "folder_id": file.folder_id,
        "file_id": file.file_id,
        "file_unique_id": file.file_unique_id,
        "file_name": file.file_name,
        "file_size": file.file_size,
        "mime_type": file.mime_type,
        "file_type": file.file_type,
        "duration": file.duration,
        "width": file.width,
        "height": file.height,
        "created_at": file.created_at,
        "updated_at": file.updated_at,
        "stream_url": f"/api/stream/{file.id}?token={media_token}",
        "thumbnail_url": f"/api/stream/{file.id}/thumbnail?token={media_token}" if file.thumbnail_file_id else None,
        "last_pos": last_pos,
        "progress_percent": progress_percent,
        "watched_state": watched_state,
        "is_favorite": bool(file.favorite_links),
        "last_watched": progress.updated_at if progress else None,
        "metadata": ({
            "title": metadata.title,
            "original_title": metadata.original_title,
            "overview": metadata.overview,
            "year": metadata.year,
            "runtime": metadata.runtime,
            "genres": json.loads(metadata.genres_json or "[]"),
            "rating": metadata.rating,
            "poster_url": metadata.poster_url,
            "backdrop_url": metadata.backdrop_url,
            "cast": json.loads(metadata.cast_json or "[]"),
            "directors": json.loads(metadata.directors_json or "[]"),
            "external_id": metadata.external_id,
            "media_type": metadata.media_type,
            "season": metadata.season,
            "episode": metadata.episode,
            "provider": metadata.provider,
            "updated_at": metadata.updated_at,
        } if metadata else None),
        "tags": tags,
    }
    
    if file.public_hash:
        data["public_hash"] = file.public_hash
        data["public_stream_url"] = f"/api/stream/s/{file.public_hash}"
        
    return data

async def fetch_recent_files(db: AsyncSession, user_id: int, limit: int) -> List[File]:
    """Get recently added files across all folders."""
    query = (
        select(File)
        .where(File.user_id == user_id)
        .options(*file_load_options())
        .order_by(desc(File.created_at))
        .limit(limit)
    )
    result = await db.execute(query)
    return result.scalars().all()

async def fetch_continue_watching_files(db: AsyncSession, user_id: int, limit: int) -> List[File]:
    """Get files with watch progress (not completed)."""
    query = (
        select(File)
        .join(WatchProgress, File.id == WatchProgress.file_id)
        .where(
            File.user_id == user_id,
            WatchProgress.user_id == user_id,
            WatchProgress.position > 0,
            WatchProgress.completed == False
        )
        .options(*file_load_options())
        .order_by(desc(WatchProgress.updated_at))
        .limit(limit)
    )
    result = await db.execute(query)
    return result.scalars().unique().all()


async def probe_file(path: Path) -> dict:
    """Run ffprobe against a downloaded media file and return its JSON output."""
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


def extract_metadata(data: dict, file_type: str) -> tuple[int | None, int | None, int | None]:
    """Extract duration and video dimensions from ffprobe JSON."""
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
    video_stream = next(
        (stream for stream in data.get("streams") or [] if stream.get("codec_type") == "video"),
        {},
    )
    return duration, video_stream.get("width"), video_stream.get("height")


async def process_one(file: File, db: AsyncSession) -> bool:
    """Download, probe, and persist one file using the already-running Telegram client."""
    client = telegram.tg_client
    if client is None or not client.is_connected:
        raise RuntimeError("Telegram client is not connected")

    temp_path: Path | None = None
    try:
        suffix = Path(file.file_name or "media.bin").suffix or ".bin"
        with tempfile.NamedTemporaryFile(prefix="teleplay-", suffix=suffix, delete=False) as handle:
            temp_path = Path(handle.name)
        message = await client.get_messages(telegram.settings.telegram_storage_channel_id, file.channel_message_id)
        await client.download_media(message, file_name=str(temp_path))
        probe = await probe_file(temp_path)
        duration, width, height = extract_metadata(probe, file.file_type)
        if duration is None:
            raise RuntimeError("ffprobe returned no duration")

        current = await db.get(File, file.id)
        if current and current.duration is None:
            current.duration = duration
            if current.file_type == "video":
                current.width = width
                current.height = height
            await db.commit()
        return True
    finally:
        if temp_path:
            temp_path.unlink(missing_ok=True)


async def backfill_media_metadata(db: AsyncSession | None = None) -> None:
    """Backfill media metadata inside the running FastAPI/Telegram process.

    The optional session is useful for tests; production background tasks open their
    own session and reuse the singleton Telegram client created by FastAPI lifespan.
    """
    owns_session = db is None
    session = db or async_session()
    try:
        total_query = select(func.count(File.id)).where(
            File.file_type.in_(("video", "audio")),
            File.duration.is_(None),
        )
        total = int((await session.execute(total_query)).scalar_one())
        query = select(File).where(
            File.file_type.in_(("video", "audio")),
            File.duration.is_(None),
        ).order_by(File.id)
        files = (await session.execute(query)).scalars().all()
        if not files:
            logger.info("Metadata backfill: no media rows need processing")
            return

        logger.info("Metadata backfill started: %d/%d media rows", len(files), total)
        processed = 0
        attempted = 0
        for file in files:
            try:
                if await process_one(file, session):
                    processed += 1
            except Exception:
                await session.rollback()
                logger.exception("Metadata backfill failed for file id %s (%s)", file.id, file.file_name)
            finally:
                attempted += 1
                if attempted % 10 == 0 or attempted == len(files):
                    logger.info(
                        "Metadata backfill progress: %d/%d attempted, %d succeeded (file id %s)",
                        attempted,
                        len(files),
                        processed,
                        file.id,
                    )
            await asyncio.sleep(0.5)
        logger.info("Metadata backfill completed: %d/%d processed", processed, len(files))
    finally:
        if owns_session:
            await session.close()
