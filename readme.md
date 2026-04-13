# LuxMusic Backend (FastAPI + uv)

MVP-бэкенд музыкального клиента в стиле Spotify/Яндекс Музыки.

Реализовано:

- регистрация и логин (JWT)
- роли `user` и `admin`
- каталог треков и поиск (`ILIKE`)
- лайки и список избранного
- плейлисты
- история прослушивания
- рекомендации на основе лайков (по жанрам)
- импорт треков из папки через минимальную админ-страницу

## Стек

- FastAPI
- SQLAlchemy 2.0 async
- SQLite (для старта)
- uv для управления окружением и запуском

## Быстрый старт

1. Установить зависимости:

```bash
uv sync
```

1. Создать `.env` в корне проекта:

```bash
copy .env.example .env
```

или вручную:

```env
SECRET_KEY=your-super-secret-key-12345
ACCESS_TOKEN_EXPIRE_MINUTES=60
SQLITE_PATH=luxmusic.db
MUSIC_DIR=music
CORS_ORIGINS=["*"]

# Админ создается автоматически при старте, если заполнены обе переменные
BOOTSTRAP_ADMIN_EMAIL=admin@luxmusic.local
BOOTSTRAP_ADMIN_PASSWORD=AdminPass123!
```

1. Запустить API:

```bash
uv run main.py
```

1. Открыть документацию:

- Swagger UI: `http://127.0.0.1:8000/docs`
- ReDoc: `http://127.0.0.1:8000/redoc`

## Импорт треков

Поддерживаемые форматы: `mp3`, `aac`, `m4a`, `flac`, `ogg`.

Ожидаемая структура папки для импорта (рекомендуется):

```text
D:\music\
  Artist One\\
    Track A.mp3
    Track B.flac
  Artist Two\\
    Song X.ogg
```

Имя папки артиста становится `artist`, имя файла (без расширения) становится `title`.

Админка импорта: `GET /admin` (требуется JWT admin через кнопку Authorize в Swagger).

Куда класть музыку:

- локально для dev: в папку `music/` в корне проекта
- рекомендуемая структура: `music/Имя_Артиста/Название_Трека.mp3`
- можно указать другую директорию через переменную `MUSIC_DIR` или поле `folder` в `POST /admin/import`

Будет ли музыка идти к клиенту для прослушивания:

- да, клиент получает аудио через `GET /tracks/id/{track_id}/stream`
- для удобства есть `GET /tracks/id/{track_id}/stream-url`, который возвращает URL для плеера
- endpoint отдает файл как потоковый HTTP-ответ, это подходит для `<audio>` в web-клиенте и для мобильных клиентов

## Основные эндпоинты

Auth:

- `POST /auth/register`
- `POST /auth/login`

User:

- `GET /users/me`

Tracks:

- `GET /tracks` (поиск через `q`)
- `GET /tracks/liked`
- `PUT /tracks/id/{track_id}/like`
- `DELETE /tracks/id/{track_id}/like`
- `POST /tracks/id/{track_id}/play`
- `GET /tracks/history`
- `GET /tracks/recommended`
- `GET /tracks/id/{track_id}/stream`
- `GET /tracks/id/{track_id}/stream-url`
- `GET /tracks/stats/popular`

Playlists:

- `POST /playlists`
- `GET /playlists`
- `PUT /playlists/{playlist_id}/tracks/{track_id}`
- `DELETE /playlists/{playlist_id}/tracks/{track_id}`

Admin:

- `GET /admin`
- `POST /admin/import`

## Пример сценария

1. Зарегистрировать пользователя: `POST /auth/register`
2. Получить токен: `POST /auth/login`
3. В Swagger нажать `Authorize` и вставить `Bearer <token>`
4. Админом импортировать музыку через `POST /admin/import`
5. Получить каталог: `GET /tracks`
6. Лайкнуть трек: `PUT /tracks/id/{track_id}/like`
7. Получить рекомендации: `GET /tracks/recommended`

## Что можно сделать дальше

- добавить refresh-токены и logout
- перейти на PostgreSQL + Alembic миграции
- сделать полноценный HTTP Range streaming
- добавить пагинацию и сортировки везде
- покрыть критические сценарии автотестами
