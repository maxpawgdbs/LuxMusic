# LuxMusic

[![Android CI](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/ci.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/ci.yml)
[![Release APK](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/release.yml)
[![Nightly APK](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/nightly.yml/badge.svg)](https://github.com/maxpawgdbs/LuxMusic/actions/workflows/nightly.yml)

Android-прототип музыкального приложения с локальной офлайн-библиотекой, плейлистами, загрузкой по ссылке и интерфейсом на Material Design 3.

## Что уже сделано

- импорт локальных аудиофайлов через системный picker
- копирование музыки во внутреннее хранилище приложения, чтобы треки были доступны офлайн
- извлечение метаданных, обложек и текста из файла или sidecar-файлов (`.lrc`, `.txt`, `.vtt`) по возможности
- плейлисты
- режимы `shuffle`, `repeat all`, `repeat one`
- UI на Jetpack Compose + Material 3
- скачивание по ссылке через `yt-dlp` Android-wrapper с последующим импортом в библиотеку
- media notification и управление воспроизведением

## Стек

- Kotlin
- Jetpack Compose
- Media3 ExoPlayer
- `io.github.junkfood02.youtubedl-android`

## CI/CD

- `Android CI` запускается на каждый push в `main`, на каждый PR и вручную
- `Release APK` запускается по тегам `v*` и вручную
- `Nightly APK` запускается по расписанию и вручную
- CI больше не загружает build artifacts в GitHub Actions storage
- Release workflow публикует один universal APK в GitHub Releases

## Signed Release

Чтобы GitHub Actions собирал подписанный release APK, добавьте в Secrets репозитория:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` должен содержать base64-строку вашего `.jks` или `.keystore` файла.  
Если secrets не заданы, workflow соберёт unsigned release локально, но в GitHub Releases опубликует installable `app-debug.apk`, а не unsigned файл.

## Важные замечания

- Загрузчик построен на GPL-зависимости. Для закрытого коммерческого релиза этот слой лучше заменить своим backend/provider-решением.
- Используйте скачивание только для контента, который вы имеете право сохранять.

## Как открыть

1. Установить Android Studio с JDK 17+.
2. Установить Android SDK Platform 36.
3. Открыть папку проекта в Android Studio.
4. Дождаться синхронизации Gradle wrapper и зависимостей.
5. Запустить `app` на устройстве или эмуляторе с Android 8.0+.
