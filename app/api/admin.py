from fastapi import APIRouter, Depends, Form
from fastapi.responses import HTMLResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_db_session
from app.deps import require_admin
from app.models import User
from app.services.importer import import_tracks_from_folder

router = APIRouter(prefix="/admin", tags=["admin"])


@router.get("", response_class=HTMLResponse)
async def admin_page(current_user: User = Depends(require_admin)) -> str:
    return """
    <!doctype html>
    <html lang=\"en\">
    <head>
      <meta charset=\"UTF-8\" />
      <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
      <title>LuxMusic Admin</title>
      <style>
        body { font-family: "Segoe UI", sans-serif; margin: 40px; background: #f5f7fa; color: #1f2937; }
        .card { max-width: 560px; margin: 0 auto; background: #fff; border-radius: 14px; padding: 24px; box-shadow: 0 8px 24px rgba(0,0,0,.08); }
        input, button { width: 100%; padding: 12px; margin-top: 12px; border-radius: 8px; border: 1px solid #d1d5db; }
        button { background: #111827; color: #fff; cursor: pointer; }
        button:hover { background: #000; }
      </style>
    </head>
    <body>
      <div class=\"card\">
        <h1>LuxMusic Admin</h1>
        <p>Импорт треков из папки (форматы: mp3/aac/m4a/flac/ogg).</p>
        <form action=\"/admin/import\" method=\"post\">
          <label for=\"folder\">Путь к папке:</label>
          <input id=\"folder\" name=\"folder\" placeholder=\"D:\\music\" required />
          <button type=\"submit\">Импортировать</button>
        </form>
      </div>
    </body>
    </html>
    """


@router.post("/import", response_class=HTMLResponse)
async def import_folder(
    folder: str = Form(...),
    _: User = Depends(require_admin),
    db: AsyncSession = Depends(get_db_session),
) -> str:
    imported = await import_tracks_from_folder(db, folder)
    return f"""
    <html>
      <body style=\"font-family: Segoe UI, sans-serif; margin: 24px;\">
        <h2>Готово</h2>
        <p>Импортировано треков: <strong>{imported}</strong></p>
        <a href=\"/admin\">Назад</a>
      </body>
    </html>
    """
