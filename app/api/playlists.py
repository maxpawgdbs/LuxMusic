from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import asc, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_db_session
from app.deps import get_current_user
from app.models import Playlist, PlaylistTrack, Track, User
from app.schemas import PlaylistCreate, PlaylistOut

router = APIRouter(prefix="/playlists", tags=["playlists"])


@router.post("", response_model=PlaylistOut, status_code=status.HTTP_201_CREATED)
async def create_playlist(
    payload: PlaylistCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> PlaylistOut:
    playlist = Playlist(name=payload.name, owner_id=current_user.id)
    db.add(playlist)
    await db.commit()
    await db.refresh(playlist)
    return PlaylistOut(id=playlist.id, name=playlist.name, owner_id=playlist.owner_id)


@router.get("", response_model=list[PlaylistOut])
async def list_playlists(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> list[PlaylistOut]:
    result = await db.execute(
        select(Playlist).where(Playlist.owner_id == current_user.id).order_by(asc(Playlist.id))
    )
    items = result.scalars().all()
    return [PlaylistOut(id=i.id, name=i.name, owner_id=i.owner_id) for i in items]


@router.put("/{playlist_id}/tracks/{track_id}", status_code=status.HTTP_201_CREATED)
async def add_track_to_playlist(
    playlist_id: int,
    track_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, str]:
    playlist = await db.get(Playlist, playlist_id)
    if playlist is None or playlist.owner_id != current_user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Playlist not found")

    track = await db.get(Track, track_id)
    if track is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Track not found")

    exists = await db.execute(
        select(PlaylistTrack).where(
            PlaylistTrack.playlist_id == playlist_id,
            PlaylistTrack.track_id == track_id,
        )
    )
    if exists.scalar_one_or_none() is None:
        db.add(PlaylistTrack(playlist_id=playlist_id, track_id=track_id))
        await db.commit()
    return {"status": "added"}


@router.delete("/{playlist_id}/tracks/{track_id}")
async def remove_track_from_playlist(
    playlist_id: int,
    track_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> dict[str, str]:
    playlist = await db.get(Playlist, playlist_id)
    if playlist is None or playlist.owner_id != current_user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Playlist not found")

    result = await db.execute(
        select(PlaylistTrack).where(
            PlaylistTrack.playlist_id == playlist_id,
            PlaylistTrack.track_id == track_id,
        )
    )
    row = result.scalar_one_or_none()
    if row is not None:
        await db.delete(row)
        await db.commit()

    return {"status": "removed"}
