package com.buddyapps.notes;

import org.json.JSONException;
import org.json.JSONObject;

final class Note {
    String id;
    String title;
    String body;
    int color;
    long updated;

    Note() {
        id = Long.toHexString(System.nanoTime());
        title = "";
        body = "";
        color = 0xFFFFFFFF;
        updated = System.currentTimeMillis();
    }

    Note(JSONObject o) {
        id = o.optString("id");
        title = o.optString("title");
        body = o.optString("body");
        color = (int) o.optLong("color", 0xFFFFFFFFL);
        updated = o.optLong("updated");
    }

    JSONObject json() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("body", body);
        o.put("color", color & 0xFFFFFFFFL);
        o.put("updated", updated);
        return o;
    }
}
