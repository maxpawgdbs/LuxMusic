"""Offline tests for the one-shot Telegram release publisher."""

import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import telegram_release as publisher


def release(tag="v0.7.3", **overrides):
    version = tag[1:]
    data = {
        "tag_name": tag,
        "html_url": f"https://github.com/maxpawgdbs/LuxMusic/releases/tag/{tag}",
        "draft": False,
        "prerelease": False,
        "body": f"LuxMusic {version} — обновлённый интерфейс.\n\nПлеер доступен без прокрутки.",
        "assets": [{
            "name": f"LuxMusic-{version}-universal.apk",
            "state": "uploaded",
            "size": 1024,
        }],
    }
    data.update(overrides)
    return data


class ReleaseValidationTests(unittest.TestCase):
    def test_accepts_published_stable_release_with_apk(self):
        self.assertEqual(
            publisher.validate_release(release()),
            ("v0.7.3", "https://github.com/maxpawgdbs/LuxMusic/releases/tag/v0.7.3"),
        )

    def test_rejects_draft_and_prerelease(self):
        for change in ({"draft": True}, {"prerelease": True}):
            with self.subTest(change=change), self.assertRaises(publisher.PublisherError):
                publisher.validate_release(release(**change))

    def test_rejects_wrong_tag_or_release_url(self):
        for change in ({"tag_name": "edge"}, {"html_url": "https://example.com/fake"}):
            with self.subTest(change=change), self.assertRaises(publisher.PublisherError):
                publisher.validate_release(release(**change))

    def test_rejects_missing_incomplete_or_empty_apk(self):
        for assets in (
            [],
            [{"name": "wrong.apk", "state": "uploaded", "size": 1024}],
            [{"name": "LuxMusic-0.7.3-universal.apk", "state": "starter", "size": 1024}],
            [{"name": "LuxMusic-0.7.3-universal.apk", "state": "uploaded", "size": 0}],
        ):
            with self.subTest(assets=assets), self.assertRaises(publisher.PublisherError):
                publisher.validate_release(release(assets=assets))

    def test_message_contains_release_notes_and_direct_link(self):
        message = publisher.build_message(release())
        self.assertIn("Плеер доступен без прокрутки.", message)
        self.assertIn("https://github.com/maxpawgdbs/LuxMusic/releases/tag/v0.7.3", message)
        self.assertLessEqual(len(message), 4096)

    def test_custom_description_replaces_release_notes(self):
        message = publisher.build_message(release(), "Новое описание\n\nВторая часть\n\nТретья часть")
        self.assertIn("Новое описание", message)
        self.assertIn("Третья часть", message)
        self.assertNotIn("Плеер доступен", message)

    def test_long_description_still_fits_telegram_limit(self):
        message = publisher.build_message(release(), "а" * 10000)
        self.assertLessEqual(len(message), 4096)
        self.assertTrue(message.endswith("/v0.7.3"))


class PublishingTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.state_path = Path(self.folder.name) / "publisher-state.json"

    @patch.object(publisher, "request_json", return_value=release())
    @patch.object(publisher, "send_message", return_value=123)
    def test_posts_once_and_exits(self, send, fetch):
        with patch.dict(os.environ, {"LUXMUSIC_TELEGRAM_BOT_TOKEN": "123:test"}):
            first = publisher.run(state_path=self.state_path, tag="v0.7.3")
            second = publisher.run(state_path=self.state_path, tag="v0.7.3")
        self.assertIn("https://t.me/luxmusic_gdbs/123", first)
        self.assertIn("уже обработан", second)
        send.assert_called_once()
        self.assertIn("v0.7.3", json.loads(self.state_path.read_text(encoding="utf-8"))["posted"])
        self.assertIn("/releases/tags/v0.7.3", fetch.call_args.args[0])

    @patch.object(publisher, "request_json", return_value=release())
    @patch.object(publisher, "send_message")
    def test_dry_run_does_not_send_or_write_state(self, send, _fetch):
        output = publisher.run(state_path=self.state_path, dry_run=True)
        self.assertIn("сообщение не отправлено", output)
        self.assertFalse(self.state_path.exists())
        send.assert_not_called()

    @patch.object(publisher, "request_json", return_value=release())
    @patch.object(publisher, "send_message")
    def test_skip_current_marks_release_without_posting(self, send, _fetch):
        publisher.run(state_path=self.state_path, skip_current=True)
        self.assertTrue(json.loads(self.state_path.read_text(encoding="utf-8"))["posted"]["v0.7.3"]["skipped"])
        send.assert_not_called()

    @patch.object(publisher, "request_json", return_value=release())
    @patch.object(publisher, "send_message", side_effect=publisher.PublisherError("Telegram отказал"))
    def test_failed_post_does_not_mark_release_done(self, _send, _fetch):
        with patch.dict(os.environ, {"LUXMUSIC_TELEGRAM_BOT_TOKEN": "123:test"}):
            with self.assertRaises(publisher.PublisherError):
                publisher.run(state_path=self.state_path)
        self.assertFalse(self.state_path.exists())

    @patch.object(publisher, "request_json", return_value=release("v0.7.4"))
    @patch.object(publisher, "send_message")
    def test_wrong_tag_never_posts(self, send, _fetch):
        with self.assertRaises(publisher.PublisherError):
            publisher.run(state_path=self.state_path, tag="v0.7.3")
        send.assert_not_called()

    def test_invalid_tag_never_fetches(self):
        with patch.object(publisher, "request_json") as fetch:
            with self.assertRaises(publisher.PublisherError):
                publisher.run(state_path=self.state_path, tag="edge")
            fetch.assert_not_called()

    def test_corrupt_state_prevents_duplicate_risk(self):
        self.state_path.write_text("not json", encoding="utf-8")
        with patch.object(publisher, "request_json", return_value=release()):
            with patch.object(publisher, "send_message") as send:
                with self.assertRaises(publisher.PublisherError):
                    publisher.run(state_path=self.state_path)
                send.assert_not_called()

    @patch.object(publisher, "request_json", return_value={
        "ok": True,
        "result": {"message_id": 19, "chat": {"type": "channel", "username": "luxmusic_gdbs"}},
    })
    def test_send_uses_channel_username_and_message_text(self, request_json):
        self.assertEqual(publisher.send_message("123:token", "Пост"), 19)
        self.assertEqual(request_json.call_args.kwargs["payload"], {
            "chat_id": "@luxmusic_gdbs", "text": "Пост",
        })

    def test_missing_token_fails_before_request(self):
        with patch.object(publisher, "request_json") as fetch:
            with self.assertRaises(publisher.PublisherError):
                publisher.send_message("", "Пост")
            fetch.assert_not_called()

    def test_reads_token_from_ignored_env_file(self):
        env_file = Path(self.folder.name) / ".env"
        env_file.write_text("# local only\nLUXMUSIC_TELEGRAM_BOT_TOKEN='123:from-file'\n", encoding="utf-8")
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(publisher.load_bot_token(env_file), "123:from-file")

    def test_environment_override_takes_precedence(self):
        with patch.dict(os.environ, {"LUXMUSIC_TELEGRAM_BOT_TOKEN": "123:override"}):
            self.assertEqual(publisher.load_bot_token(Path(self.folder.name) / "missing"), "123:override")

    def test_missing_or_empty_token_file_has_clear_error(self):
        env_file = Path(self.folder.name) / ".env"
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(publisher.PublisherError):
                publisher.load_bot_token(env_file)
            env_file.write_text("SOMETHING_ELSE=abc\n", encoding="utf-8")
            with self.assertRaises(publisher.PublisherError):
                publisher.load_bot_token(env_file)


if __name__ == "__main__":
    unittest.main()
