"""Publish a finished LuxMusic GitHub release to Telegram once, locally."""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib import error, request


REPOSITORY = "maxpawgdbs/LuxMusic"
CHANNEL = "@luxmusic_gdbs"
GITHUB_API = f"https://api.github.com/repos/{REPOSITORY}/releases/latest"
TAG_PATTERN = re.compile(r"v(\d+\.\d+\.\d+)$")
DEFAULT_ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


class PublisherError(Exception):
    """An expected, safe-to-display publisher error."""


def request_json(url: str, *, payload: dict | None = None) -> dict:
    headers = {"User-Agent": "LuxMusic-local-release-publisher/1.0"}
    data = None
    if payload is None:
        headers["Accept"] = "application/vnd.github+json"
    else:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    try:
        with request.urlopen(request.Request(url, data=data, headers=headers), timeout=25) as response:
            result = json.load(response)
    except error.HTTPError as exc:
        if "api.telegram.org" in url:
            try:
                description = json.loads(exc.read().decode("utf-8")).get("description", "")
            except (ValueError, UnicodeError):
                description = ""
            raise PublisherError(f"Telegram HTTP {exc.code}: {description or 'запрос отклонён'}") from None
        raise PublisherError(f"GitHub HTTP {exc.code}: не удалось получить релиз") from None
    except (error.URLError, OSError):
        service = "Telegram" if "api.telegram.org" in url else "GitHub"
        raise PublisherError(f"Нет соединения с {service}; повторите попытку позже") from None
    except (ValueError, UnicodeError):
        raise PublisherError("Сервис вернул некорректный JSON") from None
    if not isinstance(result, dict):
        raise PublisherError("Сервис вернул неожиданный ответ")
    return result


def validate_release(release: dict) -> tuple[str, str]:
    if release.get("draft") or release.get("prerelease"):
        raise PublisherError("Последний релиз ещё не является опубликованным стабильным релизом")
    tag = release.get("tag_name")
    if not isinstance(tag, str) or not (match := TAG_PATTERN.fullmatch(tag)):
        raise PublisherError("У последнего релиза неверный формат тега")
    url = release.get("html_url")
    expected = f"https://github.com/{REPOSITORY}/releases/tag/{tag}"
    if url != expected:
        raise PublisherError("Ссылка на релиз не совпадает с ожидаемым репозиторием")
    asset_name = f"LuxMusic-{match.group(1)}-universal.apk"
    assets = release.get("assets")
    if not isinstance(assets, list) or not any(
        isinstance(asset, dict)
        and asset.get("name") == asset_name
        and asset.get("state") == "uploaded"
        and isinstance(asset.get("size"), int)
        and asset["size"] > 0
        for asset in assets
    ):
        raise PublisherError(f"Релиз {tag} опубликован без готового APK {asset_name}")
    return tag, url


def build_message(release: dict, description: str | None = None) -> str:
    tag, url = validate_release(release)
    intro = f"🎵 LuxMusic {tag[1:]} уже доступен!"
    footer = f"Скачать APK и посмотреть релиз:\n{url}"
    body = release.get("body") if description is None else description
    if not isinstance(body, str):
        body = ""
    body = re.sub(r"\[([^]]+)\]\(https?://[^)]+\)", r"\1", body)
    body = re.sub(r"(?m)^\s{0,3}#{1,6}\s*", "", body)
    body = body.replace("`", "").replace("\r", "").strip()
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", body) if part.strip()]
    selected = paragraphs if description is not None else paragraphs[:2]
    summary = "\n\n".join(
        part for part in selected if not part.lower().startswith("asset:")
    )[:(3000 if description is not None else 1800)].rstrip()
    return "\n\n".join(part for part in (intro, summary, footer) if part)


def default_state_path() -> Path:
    base = os.environ.get("LOCALAPPDATA")
    if not base:
        base = str(Path.home() / ".local" / "state")
    return Path(base) / "LuxMusic" / "telegram-publisher-state.json"


def load_bot_token(env_file: Path = DEFAULT_ENV_FILE) -> str:
    """Read the local bot token without executing the .env file as shell code."""
    token = os.environ.get("LUXMUSIC_TELEGRAM_BOT_TOKEN", "").strip()
    if token:
        return token
    if not env_file.is_file():
        raise PublisherError(f"Нет локального файла с токеном: {env_file}")
    for line in env_file.read_text(encoding="utf-8-sig").splitlines():
        key, separator, value = line.partition("=")
        if separator and key.strip() == "LUXMUSIC_TELEGRAM_BOT_TOKEN":
            token = value.strip()
            if len(token) >= 2 and token[0] == token[-1] and token[0] in "\"'":
                token = token[1:-1]
            if token:
                return token
    raise PublisherError(f"В {env_file} нет LUXMUSIC_TELEGRAM_BOT_TOKEN")


def load_state(path: Path) -> dict:
    if not path.exists():
        return {"posted": {}}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise PublisherError(f"Не удалось прочитать состояние {path}: {type(exc).__name__}") from None
    if not isinstance(data, dict) or not isinstance(data.get("posted"), dict):
        raise PublisherError(f"Некорректный файл состояния: {path}")
    return data


def save_state(path: Path, state: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(state, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


@contextmanager
def exclusive_run(state_path: Path):
    """Use an OS file lock so a manual run cannot race the scheduled run."""
    state_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path = state_path.with_suffix(".lock")
    with lock_path.open("a+b") as handle:
        if os.name == "nt":
            import msvcrt

            handle.seek(0)
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError:
                raise PublisherError("Другой экземпляр публикации уже выполняется") from None
            try:
                yield
            finally:
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl

            try:
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                raise PublisherError("Другой экземпляр публикации уже выполняется") from None
            try:
                yield
            finally:
                fcntl.flock(handle, fcntl.LOCK_UN)


def send_message(token: str, message: str) -> int:
    if not token or ":" not in token:
        raise PublisherError("Токен бота не настроен")
    result = request_json(
        f"https://api.telegram.org/bot{token}/sendMessage",
        payload={"chat_id": CHANNEL, "text": message},
    )
    if not result.get("ok") or not isinstance(result.get("result"), dict):
        raise PublisherError(f"Telegram отклонил сообщение: {result.get('description', 'неизвестная ошибка')}")
    sent = result["result"]
    chat = sent.get("chat") or {}
    if chat.get("username") and chat["username"].lower() != CHANNEL[1:]:
        raise PublisherError("Telegram подтвердил отправку, но вернул неожиданный канал")
    if chat.get("type") and chat["type"] != "channel":
        raise PublisherError("Telegram подтвердил отправку, но вернул неожиданный канал")
    message_id = sent.get("message_id")
    if not isinstance(message_id, int):
        raise PublisherError("Telegram не вернул ID опубликованного сообщения")
    return message_id


def run(
    *,
    state_path: Path,
    tag: str | None = None,
    description: str | None = None,
    dry_run: bool = False,
    skip_current: bool = False,
) -> str:
    with exclusive_run(state_path):
        if tag is not None and not TAG_PATTERN.fullmatch(tag):
            raise PublisherError("Укажите тег формата v0.7.3")
        endpoint = GITHUB_API if tag is None else f"https://api.github.com/repos/{REPOSITORY}/releases/tags/{tag}"
        release = request_json(endpoint)
        release_tag, url = validate_release(release)
        if tag is not None and tag != release_tag:
            raise PublisherError("GitHub вернул релиз с другим тегом")
        state = load_state(state_path)
        if release_tag in state["posted"]:
            return f"{release_tag} уже обработан; повторная публикация не нужна"
        message = build_message(release, description)
        if dry_run:
            return f"Проверка пройдена; сообщение не отправлено:\n\n{message}"
        if skip_current:
            state["posted"][release_tag] = {"release_url": url, "skipped": True}
            save_state(state_path, state)
            return f"{release_tag} отмечен как уже опубликованный; повторная публикация не нужна"
        token = load_bot_token()
        message_id = send_message(token, message)
        state["posted"][release_tag] = {"release_url": url, "message_id": message_id}
        save_state(state_path, state)
        return f"Опубликован {release_tag}: https://t.me/{CHANNEL[1:]}/{message_id}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state", type=Path, default=default_state_path())
    parser.add_argument("--tag", help="Тег конкретного опубликованного релиза, например v0.7.3")
    parser.add_argument("--description-file", type=Path, help="Новый текст описания для этого поста")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Показать пост без публикации")
    mode.add_argument("--skip-current", action="store_true", help="Не публиковать текущий релиз")
    args = parser.parse_args()
    try:
        description = None
        if args.description_file is not None:
            description = args.description_file.read_text(encoding="utf-8").strip()
            if not description:
                raise PublisherError("Файл описания пуст")
        print(run(
            state_path=args.state,
            tag=args.tag,
            description=description,
            dry_run=args.dry_run,
            skip_current=args.skip_current,
        ))
    except PublisherError as exc:
        print(f"Ошибка: {exc}", file=sys.stderr)
        return 1
    except OSError as exc:
        print(f"Ошибка доступа к локальным файлам: {type(exc).__name__}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
