from pathlib import Path

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Artist, Track

SUPPORTED_EXTENSIONS = {".mp3", ".aac", ".m4a", ".flac", ".ogg"}


async def import_tracks_from_folder(db: AsyncSession, folder: str) -> int:
    base = Path(folder)
    if not base.exists() or not base.is_dir():
        return 0

    imported = 0
    for file_path in base.rglob("*"):
        if file_path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            continue

        artist_name = file_path.parent.name.strip() or "Unknown Artist"
        title = file_path.stem.strip() or "Unknown Title"

        artist = await _get_or_create_artist(db, artist_name)

        existing = await db.execute(select(Track).where(Track.file_path == str(file_path)))
        if existing.scalar_one_or_none() is not None:
            continue

        db.add(
            Track(
                title=title,
                artist_id=artist.id,
                file_path=str(file_path),
            )
        )
        imported += 1

    await db.commit()
    return imported


async def _get_or_create_artist(db: AsyncSession, artist_name: str) -> Artist:
    result = await db.execute(select(Artist).where(Artist.name == artist_name))
    artist = result.scalar_one_or_none()
    if artist is not None:
        return artist

    artist = Artist(name=artist_name)
    db.add(artist)
    await db.flush()
    return artist
