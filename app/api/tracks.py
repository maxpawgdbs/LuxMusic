from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import FileResponse
from sqlalchemy import asc, desc, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_db_session
from app.deps import get_current_user
from app.models import Artist, Like, ListenHistory, Track, User
from app.schemas import ListenHistoryOut, TrackOut
from app.services.recommendations import recommended_track_ids_for_user

router = APIRouter(prefix="/tracks", tags=["tracks"])


def _to_track_out(track: Track) -> TrackOut:
    return TrackOut(
        id=track.id,
        title=track.title,
        artist=track.artist.name,
        genre=track.genre,
        duration_seconds=track.duration_seconds,
    )


@router.get("", response_model=list[TrackOut])
async def list_tracks(
    q: str | None = Query(default=None, max_length=255),
    limit: int = Query(default=30, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    db: AsyncSession = Depends(get_db_session),
) -> list[TrackOut]:
    query = select(Track).join(Artist).order_by(asc(Track.id)).offset(offset).limit(limit)
    if q:
        pattern = f"%{q.strip()}%"
        query = query.where((Track.title.ilike(pattern)) | (Artist.name.ilike(pattern)))

    result = await db.execute(query)
    tracks = result.scalars().unique().all()
    return [_to_track_out(track) for track in tracks]


@router.get("/recommended", response_model=list[TrackOut])
async def recommended(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> list[TrackOut]:
    ids = await recommended_track_ids_for_user(db, current_user.id)
    if not ids:
        return []

    result = await db.execute(select(Track).where(Track.id.in_(ids)).join(Artist))
    tracks = result.scalars().unique().all()
    return [_to_track_out(track) for track in tracks]


@router.get("/history", response_model=list[ListenHistoryOut])
async def listen_history(
    current_user: User = Depends(get_current_user),
    limit: int = Query(default=50, ge=1, le=200),
    db: AsyncSession = Depends(get_db_session),
) -> list[ListenHistoryOut]:
    result = await db.execute(
        select(ListenHistory)
        .where(ListenHistory.user_id == current_user.id)
        .order_by(desc(ListenHistory.listened_at))
        .limit(limit)
    )
    items = result.scalars().all()
    return [ListenHistoryOut(track_id=i.track_id, listened_at=i.listened_at) for i in items]


@router.post("/id/{track_id}/play", status_code=status.HTTP_201_CREATED)
async def register_play(
    track_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, str]:
    track = await db.get(Track, track_id)
    if track is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Track not found")

    db.add(ListenHistory(user_id=current_user.id, track_id=track_id))
    await db.commit()
    return {"status": "ok"}


@router.put("/id/{track_id}/like", status_code=status.HTTP_201_CREATED)
async def like_track(
    track_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, str]:
    track = await db.get(Track, track_id)
    if track is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Track not found")

    exists = await db.execute(
        select(Like).where(Like.user_id == current_user.id, Like.track_id == track_id)
    )
    if exists.scalar_one_or_none() is None:
        db.add(Like(user_id=current_user.id, track_id=track_id))
        await db.commit()
    return {"status": "liked"}


@router.delete("/id/{track_id}/like")
async def unlike_track(
    track_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, str]:
    result = await db.execute(
        select(Like).where(Like.user_id == current_user.id, Like.track_id == track_id)
    )
    like = result.scalar_one_or_none()
    if like is not None:
        await db.delete(like)
        await db.commit()
    return {"status": "unliked"}


@router.get("/id/{track_id}/stream")
async def stream_track(track_id: int, db: AsyncSession = Depends(get_db_session)) -> FileResponse:
    result = await db.execute(select(Track).where(Track.id == track_id))
    track = result.scalar_one_or_none()
    if track is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Track not found")

    path = Path(track.file_path)
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="File not found")

    return FileResponse(path)


@router.get("/liked", response_model=list[TrackOut])
async def liked_tracks(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> list[TrackOut]:
    result = await db.execute(
        select(Track)
        .join(Like, Like.track_id == Track.id)
        .join(Artist)
        .where(Like.user_id == current_user.id)
        .order_by(desc(Like.created_at))
    )
    tracks = result.scalars().unique().all()
    return [_to_track_out(track) for track in tracks]


@router.get("/stats/popular")
async def popular_tracks(
    limit: int = Query(default=20, ge=1, le=100),
    db: AsyncSession = Depends(get_db_session),
) -> list[dict[str, int | str]]:
    result = await db.execute(
        select(Track.id, Track.title, func.count(Like.id).label("likes_count"))
        .join(Like, Like.track_id == Track.id)
        .group_by(Track.id)
        .order_by(desc(func.count(Like.id)))
        .limit(limit)
    )
    return [{"track_id": r.id, "title": r.title, "likes_count": r.likes_count} for r in result]
