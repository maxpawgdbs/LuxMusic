from datetime import datetime

from pydantic import BaseModel, EmailStr, Field

from app.models import UserRole


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserCreate(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class UserOut(BaseModel):
    id: int
    email: EmailStr
    role: UserRole


class LoginInput(BaseModel):
    email: EmailStr
    password: str


class TrackBase(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    artist: str = Field(min_length=1, max_length=255)
    genre: str | None = Field(default=None, max_length=120)
    duration_seconds: int | None = Field(default=None, ge=1)


class TrackCreate(TrackBase):
    file_path: str


class TrackOut(BaseModel):
    id: int
    title: str
    artist: str
    genre: str | None
    duration_seconds: int | None


class PlaylistCreate(BaseModel):
    name: str = Field(min_length=1, max_length=255)


class PlaylistOut(BaseModel):
    id: int
    name: str
    owner_id: int


class ListenHistoryOut(BaseModel):
    track_id: int
    listened_at: datetime
