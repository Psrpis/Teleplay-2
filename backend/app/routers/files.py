"""
File management API endpoints.
"""
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query
import secrets
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, delete
from sqlalchemy.orm import selectinload

from ..database import get_db
from ..models import Favorite, File, User, WatchProgress
from ..schemas import FileResponse, FileListResponse, FileUpdate, WatchProgressUpdate
from ..auth import get_current_user
from ..telegram import delete_from_storage_channel
from ..config import get_settings
from ..services import (
    escape_like, 
    sanitize_filename, 
    add_urls_to_file, 
    fetch_recent_files, 
    fetch_continue_watching_files
)

router = APIRouter(prefix="/files", tags=["Files"])
settings = get_settings()


@router.get("", response_model=FileListResponse)
async def list_files(
    folder_id: Optional[int] = Query(None, description="Filter by folder ID (null for root)"),
    file_type: Optional[str] = Query(None, description="Filter by file type"),
    search: Optional[str] = Query(None, description="Search by filename"),
    page: int = Query(1, ge=1),
    per_page: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List user's files with optional filtering."""
    query = select(File).where(File.user_id == current_user.id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    
    
    # Apply filters
    if folder_id is not None:
        query = query.where(File.folder_id == folder_id)
    elif not search and not file_type:
        # If simply browsing (no search/filter), only show files in root (folder_id is NULL)
        query = query.where(File.folder_id.is_(None))
        
    if file_type:
        query = query.where(File.file_type == file_type)
    if search:
        query = query.where(File.file_name.ilike(f"%{escape_like(search)}%", escape="\\"))
    
    # Get total count
    count_query = select(func.count()).select_from(query.subquery())
    total = (await db.execute(count_query)).scalar()
    
    # Apply pagination
    query = query.order_by(File.created_at.desc())
    query = query.offset((page - 1) * per_page).limit(per_page)
    
    result = await db.execute(query)
    files = result.scalars().all()
    
    return FileListResponse(
        files=[FileResponse(**add_urls_to_file(f)) for f in files],
        total=total,
        page=page,
        per_page=per_page,
    )


@router.get("/recent", response_model=FileListResponse)
async def get_recent_files(
    limit: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get recently added files across all folders."""
    files = await fetch_recent_files(db, current_user.id, limit)
    
    return FileListResponse(
        files=[FileResponse(**add_urls_to_file(f)) for f in files],
        total=len(files),
        page=1,
        per_page=limit,
    )


@router.get("/continue-watching", response_model=FileListResponse)
async def get_continue_watching(
    limit: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get files with watch progress."""
    files = await fetch_continue_watching_files(db, current_user.id, limit)
    
    return FileListResponse(
        files=[FileResponse(**add_urls_to_file(f)) for f in files],
        total=len(files),
        page=1,
        per_page=limit,
    )


@router.get("/history", response_model=FileListResponse)
async def get_watch_history(
    limit: int = Query(50, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get all watched media, most recently watched first."""
    result = await db.execute(
        select(File)
        .join(WatchProgress, File.id == WatchProgress.file_id)
        .where(File.user_id == current_user.id, WatchProgress.user_id == current_user.id)
        .options(selectinload(File.watch_progress), selectinload(File.favorites))
        .order_by(WatchProgress.updated_at.desc())
        .limit(limit)
    )
    files = result.scalars().unique().all()
    return FileListResponse(files=[FileResponse(**add_urls_to_file(f)) for f in files], total=len(files), page=1, per_page=limit)


@router.delete("/history")
async def clear_watch_history(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Clear the current user's watch history without removing media files."""
    result = await db.execute(delete(WatchProgress).where(WatchProgress.user_id == current_user.id))
    await db.commit()
    return {"message": "Watch history cleared", "deleted": result.rowcount or 0}


@router.get("/favorites", response_model=FileListResponse)
async def get_favorites(
    limit: int = Query(50, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get the user's favorite files, most recently saved first."""
    result = await db.execute(
        select(File)
        .join(Favorite, File.id == Favorite.file_id)
        .where(File.user_id == current_user.id, Favorite.user_id == current_user.id)
        .options(selectinload(File.watch_progress), selectinload(File.favorites))
        .order_by(Favorite.created_at.desc())
        .limit(limit)
    )
    files = result.scalars().unique().all()
    return FileListResponse(files=[FileResponse(**add_urls_to_file(f)) for f in files], total=len(files), page=1, per_page=limit)


@router.get("/storage", response_model=dict)
async def get_storage_stats(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get total storage usage."""
    query = select(func.sum(File.file_size)).where(File.user_id == current_user.id)
    result = await db.execute(query)
    total_size = result.scalar() or 0
    
    return {
        "total_size": total_size,
        "limit": -1  # Unlimited
    }


@router.put("/{file_id}/favorite", response_model=FileResponse)
async def add_favorite(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Save a file to the current user's favorites."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id)
        .options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one_or_none()
    if not file:
        raise HTTPException(status_code=404, detail="File not found")

    if not any(favorite.user_id == current_user.id for favorite in file.favorites):
        db.add(Favorite(user_id=current_user.id, file_id=file.id))
        await db.commit()
        result = await db.execute(
            select(File).where(File.id == file_id)
            .options(selectinload(File.watch_progress), selectinload(File.favorites))
        )
        file = result.scalar_one()
    return FileResponse(**add_urls_to_file(file))


@router.delete("/{file_id}/favorite", response_model=FileResponse)
async def remove_favorite(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Remove a file from the current user's favorites."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id)
        .options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one_or_none()
    if not file:
        raise HTTPException(status_code=404, detail="File not found")

    await db.execute(delete(Favorite).where(Favorite.user_id == current_user.id, Favorite.file_id == file_id))
    await db.commit()
    result = await db.execute(
        select(File).where(File.id == file_id)
        .options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    return FileResponse(**add_urls_to_file(result.scalar_one()))


@router.delete("/{file_id}/progress")
async def remove_progress(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Remove one file from the watch history without deleting the file."""
    result = await db.execute(
        delete(WatchProgress).where(WatchProgress.file_id == file_id, WatchProgress.user_id == current_user.id)
    )
    await db.commit()
    if not result.rowcount:
        raise HTTPException(status_code=404, detail="Watch history entry not found")
    return {"message": "Watch history entry removed"}


@router.get("/{file_id}", response_model=FileResponse)
async def get_file(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get a specific file by ID."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one_or_none()
    
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    
    return FileResponse(**add_urls_to_file(file))


@router.patch("/{file_id}", response_model=FileResponse)
async def update_file(
    file_id: int,
    update_data: FileUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update file metadata (rename, move to folder)."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id)
    )
    file = result.scalar_one_or_none()
    
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    
    # Update fields
    if update_data.file_name is not None:
        file.file_name = sanitize_filename(update_data.file_name)
    if update_data.folder_id is not None:
        file.folder_id = update_data.folder_id if update_data.folder_id != 0 else None
    
    await db.commit()
    
    # Re-fetch with relationships
    result = await db.execute(
        select(File).where(File.id == file_id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one()
    
    return FileResponse(**add_urls_to_file(file))


@router.delete("/{file_id}")
async def delete_file(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete a file from database and Telegram channel."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id)
    )
    file = result.scalar_one_or_none()
    
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    
    # Delete from Telegram storage channel
    await delete_from_storage_channel(file.channel_message_id)
    
    # Delete from database
    await db.delete(file)
    await db.commit()
    
    return {"message": "File deleted successfully"}


@router.post("/batch-delete")
async def batch_delete_files(
    file_ids: list[int],
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete multiple files."""
    # Fetch all files
    result = await db.execute(
        select(File).where(File.id.in_(file_ids), File.user_id == current_user.id)
    )
    files = result.scalars().all()
    
    if not files:
        return {"message": "No files found to delete"}
    
    # Collect message IDs for Telegram deletion
    msg_ids = [f.channel_message_id for f in files]
    
    # Delete from Telegram (batch)
    if msg_ids:
        await delete_from_storage_channel(msg_ids)
    
    # Delete from DB
    for file in files:
        await db.delete(file)
        
    await db.commit()
    
    return {"message": f"Deleted {len(files)} files"}


@router.post("/{file_id}/progress")
@router.put("/{file_id}/progress")
async def update_progress(
    file_id: int,
    progress: WatchProgressUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update watch progress. Supports both POST and PUT."""
    # Check file exists
    result = await db.execute(select(File).where(File.id == file_id, File.user_id == current_user.id))
    file = result.scalar_one_or_none()
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
        
    # Get or create progress
    result = await db.execute(
        select(WatchProgress).where(WatchProgress.file_id == file_id, WatchProgress.user_id == current_user.id)
    )
    watch_progress = result.scalar_one_or_none()
    
    if not watch_progress:
        watch_progress = WatchProgress(
            user_id=current_user.id,
            file_id=file_id,
            position=progress.position,
            duration=int(progress.duration) if progress.duration else None,
            completed=progress.completed
        )
        db.add(watch_progress)
    else:
        watch_progress.position = progress.position
        if progress.duration:
             watch_progress.duration = int(progress.duration)
        watch_progress.completed = progress.completed

    # Browsers do not always send an explicit completion event. Treat playback
    # within the final 5% as complete so it leaves "Continue Watching".
    if watch_progress.duration and watch_progress.position >= watch_progress.duration * 0.95:
        watch_progress.completed = True
        
    await db.commit()
    await db.refresh(watch_progress)
    return watch_progress


@router.get("/{file_id}/progress")
async def get_progress(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get watch progress for a file."""
    result = await db.execute(
        select(WatchProgress).where(
            WatchProgress.file_id == file_id, 
            WatchProgress.user_id == current_user.id
        )
    )
    progress = result.scalar_one_or_none()
    
    if not progress:
        return {"position": 0, "duration": 0, "completed": False}
    
    return {
        "position": progress.position,
        "duration": progress.duration or 0,
        "completed": progress.completed
    }


@router.post("/{file_id}/share", response_model=FileResponse)
async def share_file(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Generate a permanent public link for the file."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one_or_none()
    
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    
    # Generate hash if not exists or regenerate
    # Using 16 bytes = 32 hex chars
    file.public_hash = secrets.token_hex(16)
    
    await db.commit()
    
    # Re-fetch with relationships
    result = await db.execute(
        select(File).where(File.id == file_id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one()
    
    return FileResponse(**add_urls_to_file(file))


@router.delete("/{file_id}/share", response_model=FileResponse)
async def revoke_share(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Revoke the public link for the file."""
    result = await db.execute(
        select(File).where(File.id == file_id, File.user_id == current_user.id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one_or_none()
    
    if not file:
        raise HTTPException(status_code=404, detail="File not found")
    
    file.public_hash = None
    
    await db.commit()
    
    # Re-fetch with relationships
    result = await db.execute(
        select(File).where(File.id == file_id).options(selectinload(File.watch_progress), selectinload(File.favorites))
    )
    file = result.scalar_one()
    
    return FileResponse(**add_urls_to_file(file))
@router.post("/batch-move")
async def batch_move_files(
    move_data: dict,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Move multiple files to a folder."""
    file_ids = move_data.get("ids", [])
    folder_id = move_data.get("folder_id")
    
    if folder_id == 0:
        folder_id = None
        
    # Verify target folder belongs to user
    if folder_id is not None:
        from ..models import Folder
        folder_check = await db.execute(
            select(Folder).where(Folder.id == folder_id, Folder.user_id == current_user.id)
        )
        if not folder_check.scalar_one_or_none():
            raise HTTPException(status_code=404, detail="Target folder not found")
            
    # Update files
    from sqlalchemy import update
    await db.execute(
        update(File)
        .where(File.id.in_(file_ids), File.user_id == current_user.id)
        .values(folder_id=folder_id)
    )
    
    await db.commit()
    return {"message": f"Moved {len(file_ids)} files"}
