package com.luxmusic.android.data;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Opt-in two-phase check on a disposable emulator, using only platform APIs. */
public final class UpgradeFixtureInstrumentation extends Instrumentation {
    private String phase;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments == null ? null : arguments.getString("upgradePhase");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            File storage = new File(getTargetContext().getFilesDir(), "luxmusic");
            File manifest = new File(storage, "library.json");
            File audio = new File(storage, "tracks/upgrade-07-fixture.wav");
            if ("seed".equals(phase)) {
                check(!manifest.exists() && !new File(storage, "library.backup.json").exists(),
                    "Requires an empty disposable test installation; existing libraries must not be overwritten");
                File tracks = audio.getParentFile();
                check(tracks != null && (tracks.mkdirs() || tracks.isDirectory()), "Cannot create fixture directory");
                check(tracks.list() != null && tracks.list().length == 0, "Existing audio must not be overwritten");
                write(audio, wave());
                JSONObject track = new JSONObject().put("id", "upgrade-07-fixture").put("title", "Upgrade test")
                    .put("artist", "LuxMusic").put("album", "Compatibility").put("durationMs", 1000)
                    .put("localPath", audio.getAbsolutePath()).put("importedAt", 42);
                JSONObject playlist = new JSONObject().put("id", "upgrade-playlist").put("name", "Existing playlist")
                    .put("trackIds", new JSONArray().put("upgrade-07-fixture")).put("createdAt", 7);
                JSONObject root = new JSONObject().put("tracks", new JSONArray().put(track))
                    .put("playlists", new JSONArray().put(playlist)).put("artistArtworks", new JSONObject());
                write(manifest, root.toString().getBytes(StandardCharsets.UTF_8));
            } else if ("verify".equals(phase)) {
                check(Arrays.equals(wave(), read(audio)), "Audio bytes changed after APK update");
                JSONObject root = new JSONObject(new String(read(manifest), StandardCharsets.UTF_8));
                JSONObject track = find(root.getJSONArray("tracks"), "upgrade-07-fixture");
                check(audio.getAbsolutePath().equals(track.getString("localPath")), "Audio path changed");
                check("Upgrade test".equals(track.getString("title")), "Track metadata changed");
                JSONObject playlist = find(root.getJSONArray("playlists"), "upgrade-playlist");
                check("Existing playlist".equals(playlist.getString("name")), "Playlist name changed");
                check("upgrade-07-fixture".equals(playlist.getJSONArray("trackIds").getString(0)), "Playlist membership changed");
            } else {
                throw new IllegalArgumentException("Pass -e upgradePhase seed or verify");
            }
            result.putString("stream", "\nUpgrade " + phase + " OK\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError error) {
            result.putString("stream", "\nUpgrade check FAILED: " + Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static JSONObject find(JSONArray rows, String id) throws Exception {
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            if (id.equals(row.optString("id"))) return row;
        }
        throw new AssertionError("Missing saved item: " + id);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    private static void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    }

    private static byte[] read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static byte[] wave() {
        ByteBuffer bytes = ByteBuffer.allocate(16044).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(16036).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000);
        bytes.putShort((short) 2).putShort((short) 16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(16000);
        return bytes.array();
    }
}
