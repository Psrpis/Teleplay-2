"""
Database models for TelePlay streaming app.

The original file/folder/watch-progress tables remain unchanged.  The media
center tables below are intentionally additive so existing installations keep
working when ``Base.metadata.create_all`` runs at startup.
"""
from datetime import datetime
from typing import Optional, List
from sqlalchemy import BigInteger, String, Integer, Boolean, ForeignKey, DateTime, Text, Float, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from .database import Base


class User(Base):
    """Telegram user who uses the bot."""
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    telegram_id: Mapped[int] = mapped_column(BigInteger, unique=True, nullable=False, index=True)
    username: Mapped[Optional[str]] = mapped_column(String(255))
    first_name: Mapped[Optional[str]] = mapped_column(String(255))
    last_name: Mapped[Optional[str]] = mapped_column(String(255))
    auth_version: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    last_active: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    folders: Mapped[List["Folder"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    files: Mapped[List["File"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    watch_progress: Mapped[List["WatchProgress"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    favorites: Mapped[List["Favorite"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    watch_history: Mapped[List["WatchHistory"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    collections: Mapped[List["Collection"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    tags: Mapped[List["Tag"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    preferences: Mapped[List["UserPreference"]] = relationship(back_populates="user", cascade="all, delete-orphan")


class Folder(Base):
    """User-created folder for organizing files."""
    __tablename__ = "folders"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    parent_id: Mapped[Optional[int]] = mapped_column(ForeignKey("folders.id", ondelete="CASCADE"))
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="folders")
    parent: Mapped[Optional["Folder"]] = relationship(back_populates="children", remote_side=[id])
    children: Mapped[List["Folder"]] = relationship(back_populates="parent", cascade="all, delete-orphan")
    files: Mapped[List["File"]] = relationship(back_populates="folder")

    __table_args__ = (Index("idx_folder_user_parent", user_id, parent_id),)


class File(Base):
    """File stored in Telegram, metadata in database."""
    __tablename__ = "files"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    folder_id: Mapped[Optional[int]] = mapped_column(ForeignKey("folders.id", ondelete="SET NULL"))

    file_id: Mapped[str] = mapped_column(String(255), nullable=False)
    file_unique_id: Mapped[str] = mapped_column(String(255), nullable=False, index=True)
    channel_message_id: Mapped[int] = mapped_column(BigInteger, nullable=False)

    file_name: Mapped[str] = mapped_column(String(500), nullable=False)
    file_size: Mapped[int] = mapped_column(BigInteger, nullable=False)
    mime_type: Mapped[Optional[str]] = mapped_column(String(100))
    file_type: Mapped[str] = mapped_column(String(50), nullable=False)

    duration: Mapped[Optional[int]] = mapped_column(Integer)
    width: Mapped[Optional[int]] = mapped_column(Integer)
    height: Mapped[Optional[int]] = mapped_column(Integer)
    thumbnail_file_id: Mapped[Optional[str]] = mapped_column(String(255))

    public_hash: Mapped[Optional[str]] = mapped_column(String(64), unique=True, index=True)

    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="files")
    folder: Mapped[Optional["Folder"]] = relationship(back_populates="files")
    watch_progress: Mapped[List["WatchProgress"]] = relationship(back_populates="file", cascade="all, delete-orphan")
    favorite_links: Mapped[List["Favorite"]] = relationship(back_populates="file", cascade="all, delete-orphan")
    history_entries: Mapped[List["WatchHistory"]] = relationship(back_populates="file", cascade="all, delete-orphan")
    collection_links: Mapped[List["CollectionItem"]] = relationship(back_populates="file", cascade="all, delete-orphan")
    tag_links: Mapped[List["FileTag"]] = relationship(back_populates="file", cascade="all, delete-orphan")
    media_metadata: Mapped[Optional["MediaMetadata"]] = relationship(back_populates="file", uselist=False, cascade="all, delete-orphan")

    __table_args__ = (
        Index("idx_file_user_folder", user_id, folder_id),
        Index("idx_file_type", file_type),
    )


class WatchProgress(Base):
    """Current playback position for a user/file pair."""
    __tablename__ = "watch_progress"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False)
    position: Mapped[int] = mapped_column(Integer, default=0)
    duration: Mapped[Optional[int]] = mapped_column(Integer)
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="watch_progress")
    file: Mapped["File"] = relationship(back_populates="watch_progress")

    __table_args__ = (Index("idx_watch_user_file", user_id, file_id, unique=True),)


class Favorite(Base):
    """A user's favorite media item."""
    __tablename__ = "favorites"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="favorites")
    file: Mapped["File"] = relationship(back_populates="favorite_links")

    __table_args__ = (Index("idx_favorite_user_file", user_id, file_id, unique=True),)


class WatchHistory(Base):
    """Immutable viewing activity, separate from current WatchProgress."""
    __tablename__ = "watch_history"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False)
    watched_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, index=True)
    position: Mapped[Optional[int]] = mapped_column(Integer)
    duration: Mapped[Optional[int]] = mapped_column(Integer)

    user: Mapped["User"] = relationship(back_populates="watch_history")
    file: Mapped["File"] = relationship(back_populates="history_entries")

    __table_args__ = (Index("idx_history_user_watched", user_id, watched_at),)


class Collection(Base):
    """User-created logical collection, independent of physical folders."""
    __tablename__ = "collections"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[Optional[str]] = mapped_column(String(500))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="collections")
    items: Mapped[List["CollectionItem"]] = relationship(back_populates="collection", cascade="all, delete-orphan")

    __table_args__ = (Index("idx_collection_user_name", user_id, name, unique=True),)


class CollectionItem(Base):
    """Join table for media that belongs to a collection."""
    __tablename__ = "collection_items"

    id: Mapped[int] = mapped_column(primary_key=True)
    collection_id: Mapped[int] = mapped_column(ForeignKey("collections.id", ondelete="CASCADE"), nullable=False)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False)
    position: Mapped[int] = mapped_column(Integer, default=0)
    added_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    collection: Mapped["Collection"] = relationship(back_populates="items")
    file: Mapped["File"] = relationship(back_populates="collection_links")

    __table_args__ = (Index("idx_collection_item_unique", collection_id, file_id, unique=True),)


class Tag(Base):
    """Reusable user-scoped tag."""
    __tablename__ = "tags"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="tags")
    file_links: Mapped[List["FileTag"]] = relationship(back_populates="tag", cascade="all, delete-orphan")

    __table_args__ = (Index("idx_tag_user_name", user_id, name, unique=True),)


class FileTag(Base):
    """Join table assigning tags to media."""
    __tablename__ = "file_tags"

    id: Mapped[int] = mapped_column(primary_key=True)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False)
    tag_id: Mapped[int] = mapped_column(ForeignKey("tags.id", ondelete="CASCADE"), nullable=False)

    file: Mapped["File"] = relationship(back_populates="tag_links")
    tag: Mapped["Tag"] = relationship(back_populates="file_links")

    __table_args__ = (Index("idx_file_tag_unique", file_id, tag_id, unique=True),)


class MediaMetadata(Base):
    """Cached descriptive metadata; artwork never replaces the private file."""
    __tablename__ = "media_metadata"

    id: Mapped[int] = mapped_column(primary_key=True)
    file_id: Mapped[int] = mapped_column(ForeignKey("files.id", ondelete="CASCADE"), nullable=False, unique=True)
    title: Mapped[Optional[str]] = mapped_column(String(500))
    original_title: Mapped[Optional[str]] = mapped_column(String(500))
    overview: Mapped[Optional[str]] = mapped_column(Text)
    year: Mapped[Optional[int]] = mapped_column(Integer)
    runtime: Mapped[Optional[int]] = mapped_column(Integer)
    genres_json: Mapped[Optional[str]] = mapped_column(Text)
    rating: Mapped[Optional[float]] = mapped_column(Float)
    poster_url: Mapped[Optional[str]] = mapped_column(String(1000))
    backdrop_url: Mapped[Optional[str]] = mapped_column(String(1000))
    cast_json: Mapped[Optional[str]] = mapped_column(Text)
    directors_json: Mapped[Optional[str]] = mapped_column(Text)
    external_id: Mapped[Optional[str]] = mapped_column(String(120))
    media_type: Mapped[Optional[str]] = mapped_column(String(30))
    season: Mapped[Optional[int]] = mapped_column(Integer)
    episode: Mapped[Optional[int]] = mapped_column(Integer)
    provider: Mapped[Optional[str]] = mapped_column(String(50))
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    file: Mapped["File"] = relationship(back_populates="media_metadata")


class UserPreference(Base):
    """Small user-scoped preference values (home order, autoplay, etc.)."""
    __tablename__ = "user_preferences"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    key: Mapped[str] = mapped_column(String(100), nullable=False)
    value: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    user: Mapped["User"] = relationship(back_populates="preferences")

    __table_args__ = (Index("idx_preference_user_key", user_id, key, unique=True),)


class LoginCode(Base):
    """Temporary login code for TV/Web auth."""
    __tablename__ = "login_codes"

    id: Mapped[int] = mapped_column(primary_key=True)
    code: Mapped[str] = mapped_column(String(6), unique=True, index=True, nullable=False)
    telegram_id: Mapped[Optional[int]] = mapped_column(BigInteger, nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
