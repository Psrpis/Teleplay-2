import os
import sys
from pathlib import Path

os.environ.setdefault("TELEGRAM_API_ID", "1")
os.environ.setdefault("TELEGRAM_API_HASH", "test")
os.environ.setdefault("TELEGRAM_BOT_TOKEN", "test")
os.environ.setdefault("TELEGRAM_STORAGE_CHANNEL_ID", "-100123")
os.environ.setdefault("DATABASE_URL", "sqlite+aiosqlite:///./data/test.db")
os.environ.setdefault("JWT_SECRET", "12345678901234567890123456789012")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
