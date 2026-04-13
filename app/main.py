from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select

from app.api import admin, auth, playlists, tracks, users
from app.core.config import settings
from app.core.security import get_password_hash
from app.db import SessionLocal, engine
from app.models import Base, User, UserRole


@asynccontextmanager
async def lifespan(_: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    if settings.bootstrap_admin_email and settings.bootstrap_admin_password:
        async with SessionLocal() as session:
            query = select(User).where(User.email == settings.bootstrap_admin_email.lower())
            result = await session.execute(query)
            existing = result.scalar_one_or_none()
            if existing is None:
                session.add(
                    User(
                        email=settings.bootstrap_admin_email.lower(),
                        password_hash=get_password_hash(settings.bootstrap_admin_password),
                        role=UserRole.ADMIN,
                    )
                )
                await session.commit()

    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(tracks.router)
app.include_router(playlists.router)
app.include_router(admin.router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"status": "ok", "service": "luxmusic-api"}
