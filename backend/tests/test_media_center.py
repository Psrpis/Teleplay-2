import asyncio
from datetime import datetime

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.database import Base
from app.models import File, WatchProgress
from app.models import Collection, CollectionItem, User
from app.auth import create_media_token, verify_media_token
from app.routers.media import bulk_add_collection_items
from app.routers.files import list_files
from app.schemas import CollectionBulkAddRequest
from app.services import add_urls_to_file, classify_media_type, extract_metadata, parse_episode_reference, parse_filename_facets, sanitize_filename


def make_file(duration=120):
    file = File(
        id=7,
        user_id=3,
        file_id="telegram-file",
        file_unique_id="unique-file",
        channel_message_id=42,
        file_name="A Movie.mp4",
        file_size=10,
        file_type="video",
        duration=duration,
        created_at=datetime.utcnow(),
        updated_at=datetime.utcnow(),
    )
    file.watch_progress = []
    file.favorite_links = []
    file.tag_links = []
    file.media_metadata = None
    return file


def test_sanitize_filename_removes_path_traversal_and_control_chars():
    assert sanitize_filename("../secret\\movie\x00.mp4") == "_secret_movie.mp4"


def test_parse_episode_reference_supports_common_formats():
    assert parse_episode_reference("Show.Name.S01E02.1080p.mkv") == {
        "title": "Show Name",
        "season": 1,
        "episode": 2,
    }
    assert parse_episode_reference("Show Name - 2x03.mp4")["episode"] == 3
    assert parse_episode_reference("A Feature Film.mp4") is None


def test_parse_filename_facets_separates_series_and_actors():
    facets = parse_filename_facets("The.Show.S02E05.oyuncuA.And.oyuncuB.1080p.HEVC.mkv")
    assert facets["series"] == "The Show"
    assert facets["season"] == 2
    assert facets["episode"] == 5
    assert facets["actors"] == ["A", "B"]
    assert facets["quality"] == "1080p"
    assert facets["codec"] == "hevc"


def test_document_media_classification_uses_mime_and_extension():
    assert classify_media_type("video/x-matroska", "upload.bin") == "video"
    assert classify_media_type("application/octet-stream", "Movie.MKV") == "video"
    assert classify_media_type("audio/mpeg", "track.bin") == "audio"
    assert classify_media_type("application/pdf", "notes.pdf") == "document"


def test_extract_metadata_extracts_duration_and_video_dimensions():
    probe = {
        "format": {"duration": "123.6"},
        "streams": [{"codec_type": "video", "width": 1920, "height": 1080}],
    }
    assert extract_metadata(probe, "video") == (124, 1920, 1080)
    assert extract_metadata({"streams": [{"duration": "4.2"}]}, "audio") == (4, None, None)


def test_file_serialization_exposes_unwatched_state():
    serialized = add_urls_to_file(make_file())
    assert serialized["watched_state"] == "unwatched"
    assert serialized["progress_percent"] == 0
    assert serialized["is_favorite"] is False


def test_file_serialization_exposes_progress_state():
    file = make_file()
    file.watch_progress = [WatchProgress(position=60, duration=120, completed=False)]
    serialized = add_urls_to_file(file)
    assert serialized["watched_state"] == "in_progress"
    assert serialized["progress_percent"] == 50


def test_media_token_is_scoped_to_user_and_file():
    token = create_media_token(user_id=3, file_id=7)
    assert verify_media_token(token, user_id=3, file_id=7) is True
    assert verify_media_token(token, user_id=3, file_id=8) is False
    assert verify_media_token(token, user_id=4, file_id=7) is False


def test_bulk_add_collection_items_preserves_existing_and_skips_duplicates():
    async def run_test():
        engine = create_async_engine("sqlite+aiosqlite:///:memory:")
        async with engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)
        session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

        async with session_factory() as db:
            user = User(id=3, telegram_id=3003)
            existing_file = File(id=10, user_id=3, file_id="existing", file_unique_id="existing", channel_message_id=10, file_name="Show.S01E01.mkv", file_size=10, file_type="video")
            matching_file = File(id=11, user_id=3, file_id="matching", file_unique_id="matching", channel_message_id=11, file_name="Show.S01E02.mkv", file_size=10, file_type="video")
            other_file = File(id=12, user_id=3, file_id="other", file_unique_id="other", channel_message_id=12, file_name="Movie.mkv", file_size=10, file_type="video")
            collection = Collection(id=20, user_id=3, name="Show")
            collection.items = [CollectionItem(file=existing_file, position=0)]
            db.add_all([user, existing_file, matching_file, other_file, collection])
            await db.commit()

            response = await bulk_add_collection_items(20, CollectionBulkAddRequest(query="Show"), db, user)

            assert response.added_count == 1
            assert response.item_count == 2
            assert {file.file_name for file in response.files} == {"Show.S01E01.mkv", "Show.S01E02.mkv"}

        await engine.dispose()

    asyncio.run(run_test())


def test_file_list_supports_name_and_size_sorting():
    async def run_test():
        engine = create_async_engine("sqlite+aiosqlite:///:memory:")
        async with engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)
        session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

        async with session_factory() as db:
            user = User(id=4, telegram_id=4004)
            db.add_all([
                user,
                File(id=21, user_id=4, file_id="b", file_unique_id="b", channel_message_id=21, file_name="Bravo.mkv", file_size=20, file_type="video"),
                File(id=22, user_id=4, file_id="a", file_unique_id="a", channel_message_id=22, file_name="Alpha.mkv", file_size=10, file_type="video"),
            ])
            await db.commit()

            common = {"folder_id": None, "file_type": None, "search": None, "page": 1, "per_page": 20, "db": db, "current_user": user}
            by_name = await list_files(sort="name_asc", **common)
            by_size = await list_files(sort="size_desc", **common)

            assert [file.file_name for file in by_name.files] == ["Alpha.mkv", "Bravo.mkv"]
            assert [file.file_name for file in by_size.files] == ["Bravo.mkv", "Alpha.mkv"]

        await engine.dispose()

    asyncio.run(run_test())
