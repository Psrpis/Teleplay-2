"""Media-center API built on top of the existing private Telegram library."""
from __future__ import annotations

import json
import random
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import delete, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from ..auth import get_current_user
from ..database import get_db
from ..models import (
    Collection,
    CollectionItem,
    Favorite,
    File,
    FileTag,
    MediaMetadata,
    Tag,
    User,
    UserPreference,
    WatchHistory,
    WatchProgress,
)
from ..schemas import (
    CollectionCreate,
    CollectionItemUpdate,
    CollectionResponse,
    CollectionUpdate,
    FavoriteResponse,
    FileResponse,
    HistoryResponse,
    MetadataUpdate,
    PreferenceUpdate,
    TagAssignment,
    TagCreate,
    TagResponse,
    AutoTagResponse,
    WatchedStateUpdate,
)
from ..services import add_urls_to_file, auto_tag_file, escape_like, fetch_continue_watching_files, fetch_recent_files

router = APIRouter(prefix="/media", tags=["Media Center"])


def _tag_kind(name: str) -> str:
    return name.split(":", 1)[0] if ":" in name else "custom"


def _file_options():
    return (
        selectinload(File.watch_progress),
        selectinload(File.favorite_links),
        selectinload(File.media_metadata),
        selectinload(File.tag_links).selectinload(FileTag.tag),
    )


async def _get_file(db: AsyncSession, file_id: int, user_id: int) -> File:
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == user_id).options(*_file_options())
    )
    file = result.scalar_one_or_none()
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    return file


def _file_response(file: File) -> FileResponse:
    return FileResponse(**add_urls_to_file(file))


def _collection_response(collection: Collection, files: list[File] | None = None) -> CollectionResponse:
    items = collection.__dict__.get("items", [])
    loaded_files = files if files is not None else [item.__dict__.get("file") for item in items if item.__dict__.get("file")]
    return CollectionResponse(
        id=collection.id,
        name=collection.name,
        description=collection.description,
        created_at=collection.created_at,
        updated_at=collection.updated_at,
        item_count=len(items),
        files=[_file_response(file) for file in loaded_files],
    )


@router.get("/home")
async def media_home(
    limit: int = Query(20, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Return independently consumable dashboard sections in one efficient request."""
    recent = await fetch_recent_files(db, current_user.id, limit)
    continue_watching = await fetch_continue_watching_files(db, current_user.id, limit)
    favorites_result = await db.execute(
        select(File)
        .join(Favorite, Favorite.file_id == File.id)
        .where(Favorite.user_id == current_user.id, File.user_id == current_user.id)
        .options(*_file_options())
        .order_by(Favorite.created_at.desc())
        .limit(limit)
    )
    favorites = favorites_result.scalars().unique().all()
    history_result = await db.execute(
        select(WatchHistory)
        .where(WatchHistory.user_id == current_user.id)
        .options(selectinload(WatchHistory.file).options(*_file_options()))
        .order_by(WatchHistory.watched_at.desc())
        .limit(limit)
    )
    history = history_result.scalars().all()
    collections_result = await db.execute(
        select(Collection)
        .where(Collection.user_id == current_user.id)
        .options(selectinload(Collection.items))
        .order_by(Collection.updated_at.desc())
        .limit(12)
    )
    collections = collections_result.scalars().all()
    hero = recent[0] if recent else (favorites[0] if favorites else None)
    return {
        "hero": _file_response(hero) if hero else None,
        "continue_watching": [_file_response(file) for file in continue_watching],
        "favorites": [_file_response(file) for file in favorites],
        "recently_added": [_file_response(file) for file in recent],
        "recently_watched": [_file_response(entry.file) for entry in history if entry.file],
        "collections": [_collection_response(collection) for collection in collections],
    }


@router.get("/search")
async def media_search(
    q: str = Query("", max_length=200),
    file_type: Optional[str] = Query(None),
    watched: Optional[str] = Query(None, pattern="^(watched|unwatched|in_progress)$"),
    favorite: Optional[bool] = Query(None),
    tag: Optional[str] = Query(None, max_length=80),
    collection_id: Optional[int] = Query(None, ge=1),
    year: Optional[int] = Query(None, ge=1800, le=2200),
    sort: str = Query("recent", pattern="^(recent|title|year|runtime|rating)$"),
    page: int = Query(1, ge=1),
    per_page: int = Query(30, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Global, debounced-client-friendly search across private media and metadata."""
    query = select(File).where(File.user_id == current_user.id).options(*_file_options())
    metadata_joined = False
    if q.strip():
        needle = f"%{escape_like(q.strip())}%"
        query = query.outerjoin(MediaMetadata, MediaMetadata.file_id == File.id).where(
            or_(File.file_name.ilike(needle, escape="\\"), MediaMetadata.title.ilike(needle, escape="\\"))
        )
        metadata_joined = True
    if file_type:
        query = query.where(File.file_type == file_type)
    if favorite is True:
        query = query.join(Favorite, Favorite.file_id == File.id).where(Favorite.user_id == current_user.id)
    if tag:
        query = query.join(FileTag, FileTag.file_id == File.id).join(Tag, Tag.id == FileTag.tag_id).where(
            Tag.user_id == current_user.id, Tag.name.ilike(f"%{escape_like(tag)}%", escape="\\")
        )
    if collection_id:
        query = query.join(CollectionItem, CollectionItem.file_id == File.id).join(
            Collection, Collection.id == CollectionItem.collection_id
        ).where(Collection.id == collection_id, Collection.user_id == current_user.id)
    if year:
        if not metadata_joined:
            query = query.outerjoin(MediaMetadata, MediaMetadata.file_id == File.id)
            metadata_joined = True
        query = query.where(MediaMetadata.year == year)
    if watched:
        query = query.join(WatchProgress, WatchProgress.file_id == File.id).where(WatchProgress.user_id == current_user.id)
        if watched == "watched":
            query = query.where(WatchProgress.completed.is_(True))
        elif watched == "in_progress":
            query = query.where(WatchProgress.completed.is_(False), WatchProgress.position > 0)
        else:
            query = query.where(or_(WatchProgress.completed.is_(False), WatchProgress.position == 0))
    if sort == "title":
        query = query.order_by(File.file_name.asc())
    elif sort == "runtime":
        query = query.order_by(File.duration.desc().nullslast())
    elif sort in {"year", "rating"}:
        if not metadata_joined:
            query = query.outerjoin(MediaMetadata, MediaMetadata.file_id == File.id)
        query = query.order_by(
            (MediaMetadata.year if sort == "year" else MediaMetadata.rating).desc().nullslast()
        )
    else:
        query = query.order_by(File.created_at.desc())
    count_query = select(func.count()).select_from(query.order_by(None).subquery())
    total = (await db.execute(count_query)).scalar_one()
    result = await db.execute(query.offset((page - 1) * per_page).limit(per_page))
    files = result.scalars().unique().all()
    return {"files": [_file_response(file) for file in files], "total": total, "page": page, "per_page": per_page}


@router.get("/favorites", response_model=list[FileResponse])
async def list_favorites(
    db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(File).join(Favorite, Favorite.file_id == File.id)
        .where(Favorite.user_id == current_user.id, File.user_id == current_user.id)
        .options(*_file_options()).order_by(Favorite.created_at.desc())
    )
    return [_file_response(file) for file in result.scalars().unique().all()]


@router.post("/files/{file_id}/favorite", response_model=FavoriteResponse, status_code=status.HTTP_201_CREATED)
async def add_favorite(file_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    await _get_file(db, file_id, current_user.id)
    existing = await db.execute(select(Favorite).where(Favorite.file_id == file_id, Favorite.user_id == current_user.id))
    favorite = existing.scalar_one_or_none()
    if favorite:
        return favorite
    favorite = Favorite(user_id=current_user.id, file_id=file_id)
    db.add(favorite)
    await db.commit()
    await db.refresh(favorite)
    return favorite


@router.delete("/files/{file_id}/favorite", status_code=status.HTTP_204_NO_CONTENT)
async def remove_favorite(file_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    await _get_file(db, file_id, current_user.id)
    await db.execute(delete(Favorite).where(Favorite.file_id == file_id, Favorite.user_id == current_user.id))
    await db.commit()


@router.put("/files/{file_id}/watched", response_model=FileResponse)
async def set_watched_state(
    file_id: int,
    payload: WatchedStateUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    file = await _get_file(db, file_id, current_user.id)
    result = await db.execute(select(WatchProgress).where(WatchProgress.file_id == file_id, WatchProgress.user_id == current_user.id))
    progress = result.scalar_one_or_none()
    if not progress:
        progress = WatchProgress(user_id=current_user.id, file_id=file_id, position=int(file.duration or 0), duration=file.duration)
        db.add(progress)
    progress.completed = payload.watched
    if payload.watched:
        progress.position = progress.duration or file.duration or progress.position
        db.add(WatchHistory(user_id=current_user.id, file_id=file_id, position=progress.position, duration=progress.duration))
    else:
        progress.position = 0
    await db.commit()
    refreshed = await _get_file(db, file_id, current_user.id)
    return _file_response(refreshed)


@router.get("/history", response_model=list[HistoryResponse])
async def list_history(
    q: str = Query("", max_length=200), limit: int = Query(100, ge=1, le=500),
    db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)
):
    query = select(WatchHistory).where(WatchHistory.user_id == current_user.id).options(
        selectinload(WatchHistory.file).options(*_file_options())
    ).order_by(WatchHistory.watched_at.desc()).limit(limit)
    if q.strip():
        query = query.join(File, File.id == WatchHistory.file_id).where(File.file_name.ilike(f"%{escape_like(q)}%", escape="\\"))
    entries = (await db.execute(query)).scalars().all()
    return [HistoryResponse(id=e.id, file_id=e.file_id, watched_at=e.watched_at, position=e.position, duration=e.duration, file=_file_response(e.file) if e.file else None) for e in entries]


@router.delete("/history/{entry_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_history_entry(entry_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(delete(WatchHistory).where(WatchHistory.id == entry_id, WatchHistory.user_id == current_user.id))
    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="History entry not found")
    await db.commit()


@router.delete("/history", status_code=status.HTTP_204_NO_CONTENT)
async def clear_history(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    await db.execute(delete(WatchHistory).where(WatchHistory.user_id == current_user.id))
    await db.commit()


@router.get("/collections", response_model=list[CollectionResponse])
async def list_collections(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(
        select(Collection).where(Collection.user_id == current_user.id)
        .options(selectinload(Collection.items).selectinload(CollectionItem.file).options(*_file_options()))
        .order_by(Collection.name)
    )
    return [_collection_response(collection) for collection in result.scalars().all()]


@router.post("/collections", response_model=CollectionResponse, status_code=status.HTTP_201_CREATED)
async def create_collection(payload: CollectionCreate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    collection = Collection(user_id=current_user.id, name=payload.name.strip(), description=payload.description)
    db.add(collection)
    try:
        await db.commit()
    except Exception:
        await db.rollback()
        raise HTTPException(status_code=409, detail="A collection with that name already exists")
    await db.refresh(collection)
    return _collection_response(collection)


@router.patch("/collections/{collection_id}", response_model=CollectionResponse)
async def update_collection(collection_id: int, payload: CollectionUpdate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Collection).where(Collection.id == collection_id, Collection.user_id == current_user.id).options(selectinload(Collection.items)))
    collection = result.scalar_one_or_none()
    if not collection:
        raise HTTPException(status_code=404, detail="Collection not found")
    if payload.name is not None:
        collection.name = payload.name.strip()
    if payload.description is not None:
        collection.description = payload.description
    await db.commit()
    return _collection_response(collection)


@router.delete("/collections/{collection_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_collection(collection_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Collection).where(Collection.id == collection_id, Collection.user_id == current_user.id))
    collection = result.scalar_one_or_none()
    if not collection:
        raise HTTPException(status_code=404, detail="Collection not found")
    await db.delete(collection)
    await db.commit()


@router.put("/collections/{collection_id}/items", response_model=CollectionResponse)
async def replace_collection_items(collection_id: int, payload: CollectionItemUpdate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Collection).where(Collection.id == collection_id, Collection.user_id == current_user.id))
    collection = result.scalar_one_or_none()
    if not collection:
        raise HTTPException(status_code=404, detail="Collection not found")
    files_result = await db.execute(select(File).where(File.id.in_(payload.file_ids), File.user_id == current_user.id).options(*_file_options())) if payload.file_ids else None
    files = files_result.scalars().all() if files_result else []
    await db.execute(delete(CollectionItem).where(CollectionItem.collection_id == collection_id))
    for position, file in enumerate(files):
        db.add(CollectionItem(collection_id=collection_id, file_id=file.id, position=position))
    await db.commit()
    await db.refresh(collection)
    return _collection_response(collection, files)


@router.post("/files/{file_id}/auto-tag", response_model=AutoTagResponse)
async def auto_tag_single_file(file_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    file = await _get_file(db, file_id, current_user.id)
    facets = await auto_tag_file(db, file)
    await db.commit()
    return AutoTagResponse(file_id=file_id, **facets)


@router.post("/auto-tag", response_model=list[AutoTagResponse])
async def auto_tag_library(
    limit: int = Query(500, ge=1, le=5000),
    db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)
):
    result = await db.execute(
        select(File).where(File.user_id == current_user.id).options(*_file_options()).order_by(File.created_at.desc()).limit(limit)
    )
    files = result.scalars().unique().all()
    output = []
    for file in files:
        output.append(AutoTagResponse(file_id=file.id, **(await auto_tag_file(db, file))))
    await db.commit()
    return output


@router.get("/tags", response_model=list[TagResponse])
async def list_tags(
    kind: Optional[str] = Query(None, pattern="^(series|actor|quality|codec|custom)$"),
    db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)
):
    result = await db.execute(select(Tag, func.count(FileTag.id)).outerjoin(FileTag).where(Tag.user_id == current_user.id).group_by(Tag.id).order_by(Tag.name))
    return [TagResponse(id=tag.id, name=tag.name.split(":", 1)[-1], created_at=tag.created_at, file_count=count, kind=_tag_kind(tag.name), value=tag.name) for tag, count in result.all() if not kind or _tag_kind(tag.name) == kind]


@router.post("/tags", response_model=TagResponse, status_code=status.HTTP_201_CREATED)
async def create_tag(payload: TagCreate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    tag = Tag(user_id=current_user.id, name=payload.name.strip().lstrip("#"))
    db.add(tag)
    try:
        await db.commit()
    except Exception:
        await db.rollback()
        raise HTTPException(status_code=409, detail="A tag with that name already exists")
    await db.refresh(tag)
    return TagResponse(id=tag.id, name=tag.name, created_at=tag.created_at, file_count=0, kind="custom", value=tag.name)


@router.delete("/tags/{tag_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_tag(tag_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(Tag).where(Tag.id == tag_id, Tag.user_id == current_user.id))
    tag = result.scalar_one_or_none()
    if not tag:
        raise HTTPException(status_code=404, detail="Tag not found")
    await db.delete(tag)
    await db.commit()


@router.put("/files/{file_id}/tags", response_model=FileResponse)
async def replace_file_tags(file_id: int, payload: TagAssignment, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    file = await _get_file(db, file_id, current_user.id)
    tag_result = await db.execute(select(Tag).where(Tag.id.in_(payload.tag_ids), Tag.user_id == current_user.id)) if payload.tag_ids else None
    tags = tag_result.scalars().all() if tag_result else []
    await db.execute(delete(FileTag).where(FileTag.file_id == file_id))
    for tag in tags:
        db.add(FileTag(file_id=file_id, tag_id=tag.id))
    await db.commit()
    return _file_response(await _get_file(db, file_id, current_user.id))


@router.get("/files/{file_id}/metadata")
async def get_metadata(file_id: int, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    file = await _get_file(db, file_id, current_user.id)
    return add_urls_to_file(file).get("metadata") or {}


@router.put("/files/{file_id}/metadata")
async def upsert_metadata(file_id: int, payload: MetadataUpdate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    file = await _get_file(db, file_id, current_user.id)
    result = await db.execute(select(MediaMetadata).where(MediaMetadata.file_id == file_id))
    metadata = result.scalar_one_or_none()
    if not metadata:
        metadata = MediaMetadata(file_id=file_id)
        db.add(metadata)
    data = payload.model_dump(exclude_unset=True)
    for field, value in data.items():
        if field == "genres":
            setattr(metadata, "genres_json", json.dumps(value))
        elif field == "cast":
            setattr(metadata, "cast_json", json.dumps(value))
        elif field == "directors":
            setattr(metadata, "directors_json", json.dumps(value))
        else:
            setattr(metadata, field, value)
    await db.commit()
    return add_urls_to_file(await _get_file(db, file_id, current_user.id)).get("metadata")


@router.get("/stats")
async def get_statistics(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    watched_count = (await db.execute(select(func.count(WatchProgress.id)).where(WatchProgress.user_id == current_user.id, WatchProgress.completed.is_(True)))).scalar_one()
    movie_count = (await db.execute(select(func.count(WatchProgress.id)).join(File, File.id == WatchProgress.file_id).where(WatchProgress.user_id == current_user.id, WatchProgress.completed.is_(True), File.file_type == "video"))).scalar_one()
    episode_count = max(0, watched_count - movie_count)
    watch_time = (await db.execute(select(func.coalesce(func.sum(WatchHistory.duration), 0)).where(WatchHistory.user_id == current_user.id))).scalar_one()
    recent_result = await db.execute(select(WatchHistory).where(WatchHistory.user_id == current_user.id).order_by(WatchHistory.watched_at.desc()).limit(8).options(selectinload(WatchHistory.file).options(*_file_options())))
    return {"total_watched": watched_count, "movies_watched": movie_count, "episodes_watched": episode_count, "total_watch_time": int(watch_time or 0), "recent_activity": [_file_response(entry.file) for entry in recent_result.scalars().all() if entry.file]}


@router.get("/surprise", response_model=FileResponse)
async def surprise_me(
    file_type: Optional[str] = Query(None), unwatched: bool = Query(False), favorite: bool = Query(False),
    db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)
):
    query = select(File).where(File.user_id == current_user.id).options(*_file_options())
    if file_type:
        query = query.where(File.file_type == file_type)
    if favorite:
        query = query.join(Favorite, Favorite.file_id == File.id).where(Favorite.user_id == current_user.id)
    if unwatched:
        query = query.outerjoin(WatchProgress, (WatchProgress.file_id == File.id) & (WatchProgress.user_id == current_user.id)).where(or_(WatchProgress.id.is_(None), WatchProgress.completed.is_(False)))
    files = (await db.execute(query.limit(200))).scalars().unique().all()
    if not files:
        raise HTTPException(status_code=404, detail="No media matches those filters")
    return _file_response(random.choice(files))


@router.get("/preferences")
async def get_preferences(db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(UserPreference).where(UserPreference.user_id == current_user.id))
    return {item.key: item.value for item in result.scalars().all()}


@router.put("/preferences")
async def set_preference(payload: PreferenceUpdate, db: AsyncSession = Depends(get_db), current_user: User = Depends(get_current_user)):
    result = await db.execute(select(UserPreference).where(UserPreference.user_id == current_user.id, UserPreference.key == payload.key))
    preference = result.scalar_one_or_none()
    if preference:
        preference.value = payload.value
    else:
        db.add(UserPreference(user_id=current_user.id, key=payload.key, value=payload.value))
    await db.commit()
    return {"key": payload.key, "value": payload.value}
