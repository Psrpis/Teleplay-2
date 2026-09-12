"""Reclassify existing Telegram document rows using MIME type and filename."""
from __future__ import annotations

import argparse
import asyncio
import logging

from sqlalchemy import select

from ..database import async_session
from ..models import File
from ..services import classify_media_type

logger = logging.getLogger("teleplay.backfill_file_types")


async def backfill(limit: int | None = None) -> int:
    """Update document rows only; safe to run repeatedly."""
    async with async_session() as db:
        query = select(File).where(File.file_type == "document").order_by(File.id)
        if limit:
            query = query.limit(limit)
        files = (await db.execute(query)).scalars().all()
        changed = 0
        for file in files:
            detected = classify_media_type(file.mime_type, file.file_name)
            if detected == file.file_type:
                continue
            file.file_type = detected
            changed += 1
            logger.info("Reclassified file %s (%s) as %s", file.id, file.file_name, detected)
        if changed:
            await db.commit()
        logger.info("Reclassified %d/%d document rows", changed, len(files))
        return changed


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=None, help="Process at most this many document rows")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    await backfill(args.limit)


if __name__ == "__main__":
    asyncio.run(main())
