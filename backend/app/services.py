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
from sqlalchemy import delete, select, desc, func, or_, and_
from sqlalchemy.orm import selectinload

from .models import File, WatchProgress, Folder, FileTag, Tag, MediaMetadata, Favorite, WatchHistory, CollectionItem
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


def normalize_search_text(value: str) -> str:
    """Normalize free-text search input: dots/underscores -> spaces, collapsed
    whitespace. Lets a query like "Mira Luv" match filenames/tags stored as
    "Mira.Luv" or "Mira_Luv"."""
    text = re.sub(r"[._]+", " ", value or "")
    text = re.sub(r"\s+", " ", text).strip()
    return text


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


SERIES_PREFIX_ALIASES = {
    "latinamilf": "LatinaMilf",
    "lifeselector": "LifeSelector",
    "lucidflix": "LucidFlix",
    "mylfseeker": "MYLFSeeker",
    "manyvids": "ManyVids",
    "mypovfam": "MyPOVFam",
    "onlyfans": "OnlyFans",
    "pervertedpov": "PervertedPOV",
    "scottstark householdfantasy": "ScottStark HouseholdFantasy",
    "sexwithmuslims": "SexWithMuslims",
    "xvideosred": "XVideosRed",
}


def canonical_series_name(value: str) -> str:
    """Collapse known filename variants into one stable Series tag.

    Many libraries contain compact publisher/brand prefixes followed by a
    date, episode number, or title.  A trailing scrape counter (for example
    ``XVideosRed1``) and inconsistent casing previously produced separate
    tags for the same series.  Only known compact prefixes are normalized so
    ordinary titles containing meaningful numbers remain untouched.
    """
    cleaned = re.sub(r"\s+", " ", value).strip(" ._-")
    compact_key = re.sub(r"[^a-z0-9]+", "", cleaned.casefold())
    compact_without_counter = re.sub(r"\d+$", "", compact_key)
    for alias_key, canonical in SERIES_PREFIX_ALIASES.items():
        alias_compact = re.sub(r"[^a-z0-9]+", "", alias_key.casefold())
        if compact_key == alias_compact or compact_without_counter == alias_compact:
            return canonical
        prefix = re.match(rf"^{re.escape(alias_key)}(?:\s|$)", cleaned, re.IGNORECASE)
        if prefix:
            return canonical
    return cleaned


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
        "series": canonical_series_name(series_title),
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
    affected_tags: dict[int, Tag] = {}
    for link, tag in old_links.all():
        if tag.name not in desired_names:
            affected_tags[tag.id] = tag
            await db.delete(link)
    await db.flush()
    for tag in affected_tags.values():
        link_count = await db.scalar(
            select(func.count()).select_from(FileTag).where(FileTag.tag_id == tag.id)
        )
        if not link_count:
            await db.execute(delete(Tag).where(Tag.id == tag.id))
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


async def generate_thumbnail(source_path: Path, duration: int | None) -> Path | None:
    """Grab a single frame as a JPEG thumbnail using ffmpeg. Returns the temp
    thumbnail path on success, or None if extraction failed (never raises —
    a missing thumbnail shouldn't block duration/dimension backfill)."""
    # Pick a safe seek point: a few seconds in, but never past the midpoint
    # of very short clips.
    seek = 3
    if duration is not None and duration > 0:
        seek = max(0, min(seek, duration // 2))
    fd, thumb_path_str = tempfile.mkstemp(prefix="teleplay-thumb-", suffix=".jpg")
    import os as _os
    _os.close(fd)
    thumb_path = Path(thumb_path_str)
    process = await asyncio.create_subprocess_exec(
        "ffmpeg", "-y", "-ss", str(seek), "-i", str(source_path),
        "-vframes", "1", "-q:v", "2", str(thumb_path),
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
    )
    await process.communicate()
    if process.returncode != 0 or not thumb_path.exists() or thumb_path.stat().st_size == 0:
        thumb_path.unlink(missing_ok=True)
        return None
    return thumb_path


async def process_one(file: File, db: AsyncSession) -> bool:
    """Download, probe, and persist one file using the already-running Telegram client.

    Also opportunistically backfills a thumbnail for video files that don't
    have one yet (common for files uploaded as raw Telegram "documents",
    which never get an auto-generated thumbnail), reusing the same
    downloaded temp file rather than downloading twice.
    """
    client = telegram.tg_client
    if client is None or not client.is_connected:
        raise RuntimeError("Telegram client is not connected")

    temp_path: Path | None = None
    thumb_path: Path | None = None
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

        if current and current.file_type == "video" and not current.thumbnail_file_id:
            try:
                thumb_path = await generate_thumbnail(temp_path, duration)
                if thumb_path:
                    sent = await client.send_photo(telegram.settings.telegram_storage_channel_id, photo=str(thumb_path))
                    if sent and sent.photo:
                        current.thumbnail_file_id = sent.photo.file_id
                        await db.commit()
            except Exception:
                await db.rollback()
                logger.exception("Thumbnail backfill failed for file id %s (%s)", file.id, file.file_name)
        return True
    finally:
        if temp_path:
            temp_path.unlink(missing_ok=True)
        if thumb_path:
            thumb_path.unlink(missing_ok=True)


async def backfill_media_metadata(db: AsyncSession | None = None) -> None:
    """Backfill media metadata inside the running FastAPI/Telegram process.

    The optional session is useful for tests; production background tasks open their
    own session and reuse the singleton Telegram client created by FastAPI lifespan.
    """
    owns_session = db is None
    session = db or async_session()
    try:
        # Covers two cases that both stem from files uploaded as raw Telegram
        # "documents": missing duration/dimensions, and (for videos) a
        # missing thumbnail — either one alone is enough to reprocess a file.
        needs_backfill = or_(
            File.duration.is_(None),
            and_(File.file_type == "video", File.thumbnail_file_id.is_(None)),
        )
        total_query = select(func.count(File.id)).where(
            File.file_type.in_(("video", "audio")),
            needs_backfill,
        )
        total = int((await session.execute(total_query)).scalar_one())
        query = select(File).where(
            File.file_type.in_(("video", "audio")),
            needs_backfill,
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


async def merge_duplicate_files(db: AsyncSession, user_id: int) -> dict:
    """Find files with the same Telegram file_unique_id for this user (the
    same physical file added more than once by accident) and merge them
    into a single entry: favorites/tags/collections/watch history/progress
    from the extra copies are folded into the oldest copy, then the extra
    File rows are deleted. The underlying Telegram messages are left alone.
    """
    result = await db.execute(
        select(File.file_unique_id)
        .where(File.user_id == user_id)
        .group_by(File.file_unique_id)
        .having(func.count(File.id) > 1)
    )
    duplicate_unique_ids = [row[0] for row in result.all()]

    groups_merged = 0
    files_removed = 0

    for unique_id in duplicate_unique_ids:
        result = await db.execute(
            select(File)
            .where(File.user_id == user_id, File.file_unique_id == unique_id)
            .options(
                selectinload(File.watch_progress),
                selectinload(File.favorite_links),
                selectinload(File.tag_links),
                selectinload(File.collection_links),
                selectinload(File.media_metadata),
            )
            .order_by(File.id.asc())
        )
        copies = result.scalars().all()
        if len(copies) < 2:
            continue

        keeper, extras = copies[0], copies[1:]
        groups_merged += 1

        for dup in extras:
            # Watch progress: keep the furthest-along position.
            for progress in dup.watch_progress:
                keeper_progress = next((p for p in keeper.watch_progress if p.user_id == progress.user_id), None)
                if keeper_progress is None:
                    progress.file_id = keeper.id
                    keeper.watch_progress.append(progress)
                else:
                    if (progress.position or 0) > (keeper_progress.position or 0):
                        keeper_progress.position = progress.position
                        keeper_progress.duration = progress.duration
                        keeper_progress.completed = keeper_progress.completed or progress.completed
                    await db.delete(progress)

            # Favorite: keep a single favorite link if either copy was favorited.
            for fav in dup.favorite_links:
                if not any(f.user_id == fav.user_id for f in keeper.favorite_links):
                    fav.file_id = keeper.id
                    keeper.favorite_links.append(fav)
                else:
                    await db.delete(fav)

            # Tags: union of both copies' tags on the keeper.
            for link in dup.tag_links:
                if not any(t.tag_id == link.tag_id for t in keeper.tag_links):
                    link.file_id = keeper.id
                    keeper.tag_links.append(link)
                else:
                    await db.delete(link)

            # Collections: keeper ends up in every collection either copy was in.
            for item in dup.collection_links:
                if not any(c.collection_id == item.collection_id for c in keeper.collection_links):
                    item.file_id = keeper.id
                    keeper.collection_links.append(item)
                else:
                    await db.delete(item)

            # Watch history: no uniqueness constraint, just repoint entries.
            await db.execute(
                WatchHistory.__table__.update().where(WatchHistory.file_id == dup.id).values(file_id=keeper.id)
            )

            # Metadata: keep the keeper's if it has any, otherwise adopt the duplicate's.
            if dup.media_metadata and not keeper.media_metadata:
                dup.media_metadata.file_id = keeper.id
                keeper.media_metadata = dup.media_metadata
            elif dup.media_metadata:
                await db.delete(dup.media_metadata)

            await db.flush()
            await db.delete(dup)
            files_removed += 1

    if groups_merged:
        await db.commit()

    return {"duplicate_groups_merged": groups_merged, "files_removed": files_removed}
