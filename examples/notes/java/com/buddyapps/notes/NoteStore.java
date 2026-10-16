package com.buddyapps.notes;

import android.content.Context;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Notes persisted as a JSON array in the app's private storage (no database needed). */
final class NoteStore {
    private final File file;

    NoteStore(Context c) {
        file = new File(c.getFilesDir(), "notes.json");
    }

    List<Note> load() {
        List<Note> out = new ArrayList<>();
        if (!file.exists()) return out;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = in.read(buf);
            JSONArray arr = new JSONArray(new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) out.add(new Note(arr.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    void save(List<Note> notes) {
        JSONArray arr = new JSONArray();
        try {
            for (Note n : notes) arr.put(n.json());
            try (FileOutputStream o = new FileOutputStream(file)) {
                o.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}
