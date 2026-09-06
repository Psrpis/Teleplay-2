from datetime import datetime

from app.models import File, WatchProgress
from app.services import add_urls_to_file, parse_episode_reference, sanitize_filename


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
