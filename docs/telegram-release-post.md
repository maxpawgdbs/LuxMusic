# Одноразовая публикация релиза в Telegram

После публикации стабильного GitHub Release запустите локально одну команду:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\post_telegram_release.ps1 -Tag v0.7.4 -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\post_telegram_release.ps1 -Tag v0.7.4
```

Перед отправкой скрипт проверяет, что указанный релиз опубликован, не помечен как prerelease и содержит готовый универсальный APK. Токен берётся из локального `.env` в корне проекта (переменная `LUXMUSIC_TELEGRAM_BOT_TOKEN`); этот файл добавлен в `.gitignore` и не попадёт в Git. Для новой копии репозитория можно скопировать `.env.example` в `.env` и вписать токен. Переменная окружения с тем же именем имеет приоритет над файлом. Бот должен быть администратором канала `@luxmusic_gdbs` с правом публикации. Пост содержит описание из соответствующего GitHub Release и прямую ссылку на страницу релиза; APK в Telegram не загружается. После одного запроса к Telegram скрипт завершается, фонового процесса и расписания нет.

Для нового текста поста создайте локальный UTF-8 файл и передайте его как описание:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\post_telegram_release.ps1 -Tag v0.7.5 -DescriptionFile .\post-v0.7.5.txt -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\post_telegram_release.ps1 -Tag v0.7.5 -DescriptionFile .\post-v0.7.5.txt
```

Текст файла заменяет описание из GitHub Release; заголовок с версией и ссылка на релиз добавляются автоматически. Не кладите в файл токен или другие секреты. После успешной отправки тег сохраняется в `%LOCALAPPDATA%\LuxMusic\telegram-publisher-state.json`; повторный запуск ничего не публикует. Если релиз уже был вручную анонсирован, можно пометить его без отправки:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\post_telegram_release.ps1 -Tag v0.7.3 -SkipCurrent
```

`ExecutionPolicy Bypass` действует только для запущенного процесса PowerShell и не меняет системную политику. Нужен Python 3.10+; Docker и сторонние Python-пакеты не требуются. Локальные тесты:

```powershell
python -m unittest discover -s scripts -p test_telegram_release.py -v
```

Если бот-токен ранее публиковался в переписке, перевыпустите его через BotFather перед запуском. Токен для `my.telegram.org/apps` здесь не нужен.
