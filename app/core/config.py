from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    app_name: str = "LuxMusic API"
    secret_key: str = Field(default="change-me-in-env", min_length=12)
    access_token_expire_minutes: int = 60

    sqlite_path: str = "luxmusic.db"
    cors_origins: list[str] = ["*"]

    bootstrap_admin_email: str | None = None
    bootstrap_admin_password: str | None = None


settings = Settings()
