from sqlalchemy import desc, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Like, Track


async def recommended_track_ids_for_user(db: AsyncSession, user_id: int, limit: int = 20) -> list[int]:
    liked_subquery = select(Like.track_id).where(Like.user_id == user_id).subquery()

    popular_genres_subquery = (
        select(Track.genre)
        .join(Like, Like.track_id == Track.id)
        .where(Like.user_id == user_id, Track.genre.is_not(None))
        .group_by(Track.genre)
        .order_by(desc(func.count(Like.id)))
        .limit(3)
        .subquery()
    )

    query = (
        select(Track.id)
        .where(
            Track.id.not_in(select(liked_subquery.c.track_id)),
            Track.genre.in_(select(popular_genres_subquery.c.genre)),
        )
        .limit(limit)
    )

    result = await db.execute(query)
    return [row[0] for row in result.all()]
