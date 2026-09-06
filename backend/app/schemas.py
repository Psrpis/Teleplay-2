"""
Pydantic schemas for API request/response validation.
"""
from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, ConfigDict, Field


# ============== User Schemas ==============

class UserBase(BaseModel):
    telegram_id: int
    username: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None


class UserCreate(UserBase):
    pass


class UserResponse(UserBase):
    id: int
    created_at: datetime
    last_active: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============== Folder Schemas ==============

class FolderBase(BaseModel):
    name: str
    parent_id: Optional[int] = None


class FolderCreate(FolderBase):
    pass


class FolderUpdate(BaseModel):
    name: Optional[str] = None
    parent_id: Optional[int] = None


class FolderResponse(FolderBase):
    id: int
    user_id: int
    created_at: datetime
    updated_at: datetime
    file_count: int = 0
    
    model_config = ConfigDict(from_attributes=True)


class FolderWithChildren(FolderResponse):
    children: List["FolderWithChildren"] = []
    

# ============== File Schemas ==============

class FileBase(BaseModel):
    file_name: str
    file_size: int
    mime_type: Optional[str] = None
    file_type: str  # video, audio, document, image
    duration: Optional[float] = None
    width: Optional[int] = None
    height: Optional[int] = None


class FileCreate(FileBase):
    file_id: str
    file_unique_id: str
    channel_message_id: int
    thumbnail_file_id: Optional[str] = None
    folder_id: Optional[int] = None


class FileUpdate(BaseModel):
    file_name: Optional[str] = None
    folder_id: Optional[int] = None


class FileResponse(FileBase):
    id: int
    user_id: int
    folder_id: Optional[int] = None
    file_id: str
    file_unique_id: str
    created_at: datetime
    updated_at: datetime
    thumbnail_url: Optional[str] = None
    stream_url: Optional[str] = None
    download_url: Optional[str] = None
    public_hash: Optional[str] = None
    public_stream_url: Optional[str] = None
    last_pos: int = 0
    progress_percent: float = 0
    watched_state: str = "unwatched"
    is_favorite: bool = False
    last_watched: Optional[datetime] = None
    metadata: Optional[dict] = None
    tags: List[str] = Field(default_factory=list)
    
    model_config = ConfigDict(from_attributes=True)


class FileListResponse(BaseModel):
    files: List[FileResponse]
    total: int
    page: int
    per_page: int


# ============== Watch Progress Schemas ==============

class WatchProgressBase(BaseModel):
    position: int
    duration: Optional[float] = None
    completed: bool = False


class WatchProgressUpdate(BaseModel):
    position: int
    duration: Optional[float] = None


class WatchProgressResponse(WatchProgressBase):
    id: int
    file_id: int
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)


# ============== Auth Schemas ==============

class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshTokenRequest(BaseModel):
    """Request body for token refresh."""
    refresh_token: str = Field(..., alias="refreshToken")
    
    model_config = ConfigDict(populate_by_name=True)


class TokenPayload(BaseModel):
    sub: int  # user telegram_id
    exp: datetime


class LoginCodeRequest(BaseModel):
    code: str


class LoginCodeResponse(BaseModel):
    code: str
    expires_at: datetime


class VerifyCodeRequest(BaseModel):
    code: str


class AuthResponse(Token):
    user: UserResponse


class BotInfoResponse(BaseModel):
    username: str
    name: Optional[str] = None
    server_version: str = "1.0.0"


# ============== Media Center Schemas ==============

class FavoriteResponse(BaseModel):
    file_id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class WatchedStateUpdate(BaseModel):
    watched: bool


class HistoryResponse(BaseModel):
    id: int
    file_id: int
    watched_at: datetime
    position: Optional[int] = None
    duration: Optional[int] = None
    file: Optional[FileResponse] = None

    model_config = ConfigDict(from_attributes=True)


class CollectionCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)
    description: Optional[str] = Field(default=None, max_length=500)


class CollectionUpdate(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=120)
    description: Optional[str] = Field(default=None, max_length=500)


class CollectionItemUpdate(BaseModel):
    file_ids: List[int] = Field(default_factory=list, max_length=500)


class CollectionResponse(BaseModel):
    id: int
    name: str
    description: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    item_count: int = 0
    files: List[FileResponse] = Field(default_factory=list)


class TagCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=80)


class TagResponse(BaseModel):
    id: int
    name: str
    created_at: datetime
    file_count: int = 0
    kind: str = "custom"
    value: Optional[str] = None


class TagAssignment(BaseModel):
    tag_ids: List[int] = Field(default_factory=list, max_length=100)


class AutoTagResponse(BaseModel):
    file_id: int
    series: Optional[str] = None
    actors: List[str] = Field(default_factory=list)
    season: Optional[int] = None
    episode: Optional[int] = None
    quality: Optional[str] = None
    codec: Optional[str] = None
    tags: List[str] = Field(default_factory=list)


class MetadataUpdate(BaseModel):
    title: Optional[str] = Field(default=None, max_length=500)
    original_title: Optional[str] = Field(default=None, max_length=500)
    overview: Optional[str] = Field(default=None, max_length=10000)
    year: Optional[int] = Field(default=None, ge=1800, le=2200)
    runtime: Optional[int] = Field(default=None, ge=0, le=100000)
    genres: List[str] = Field(default_factory=list, max_length=30)
    rating: Optional[float] = Field(default=None, ge=0, le=10)
    poster_url: Optional[str] = Field(default=None, max_length=1000)
    backdrop_url: Optional[str] = Field(default=None, max_length=1000)
    cast: List[str] = Field(default_factory=list, max_length=100)
    directors: List[str] = Field(default_factory=list, max_length=20)
    external_id: Optional[str] = Field(default=None, max_length=120)
    media_type: Optional[str] = Field(default=None, max_length=30)
    season: Optional[int] = Field(default=None, ge=0, le=1000)
    episode: Optional[int] = Field(default=None, ge=0, le=10000)
    provider: Optional[str] = Field(default=None, max_length=50)


class PreferenceUpdate(BaseModel):
    key: str = Field(..., min_length=1, max_length=100)
    value: str = Field(..., max_length=10000)


# Resolve forward references
FolderWithChildren.model_rebuild()
