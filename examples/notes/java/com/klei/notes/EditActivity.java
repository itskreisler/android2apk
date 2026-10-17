package com.klei.notes;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Notion-style editor: big title, plain body, a colour row and save/delete. */
public class EditActivity extends Activity {
    static final int[] COLORS = {
            0xFFFFFFFF, 0xFFF28B82, 0xFFFBBC04, 0xFFFFF475, 0xFFCCFF90,
            0xFFA7FFEB, 0xFFCBF0F8, 0xFFD7AEFB, 0xFFFDCFE8
    };

    private final List<Note> notes = new ArrayList<>();
    private NoteStore store;
    private Note note;
    private EditText title, body;
    private LinearLayout colorRow;
    private int color;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new NoteStore(this);
        notes.addAll(store.load());

        String id = getIntent().getStringExtra("id");
        note = find(id);
        boolean isNew = note == null;
        if (isNew) note = new Note();
        color = note.color;

        setContentView(R.layout.activity_edit);
        title = findViewById(R.id.title);
        body = findViewById(R.id.body);
        colorRow = findViewById(R.id.color_row);

        title.setText(note.title);
        body.setText(note.body);

        buildColors();
        findViewById(R.id.delete).setVisibility(isNew ? View.GONE : View.VISIBLE);
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.save).setOnClickListener(v -> save());
        findViewById(R.id.delete).setOnClickListener(v -> {
            int idx = indexOf(note.id);
            if (idx >= 0) notes.remove(idx);
            store.save(notes);
            finish();
        });
    }

    private void buildColors() {
        colorRow.removeAllViews();
        int size = dp(34);
        for (int c : COLORS) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, dp(12), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(dot(c, c == color));
            final int chosen = c;
            dot.setOnClickListener(v -> {
                color = chosen;
                buildColors();
            });
            colorRow.addView(dot);
        }
    }

    private GradientDrawable dot(int c, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(c);
        d.setStroke(dp(selected ? 3 : 1), selected ? 0xFF1F1F1F : 0x33000000);
        return d;
    }

    private void save() {
        note.title = title.getText().toString().trim();
        note.body = body.getText().toString().trim();
        note.color = color;
        note.updated = System.currentTimeMillis();

        int idx = indexOf(note.id);
        boolean blank = note.title.isEmpty() && note.body.isEmpty();
        if (blank) {
            if (idx >= 0) notes.remove(idx);
        } else if (idx >= 0) {
            notes.set(idx, note);
        } else {
            notes.add(note);
        }
        store.save(notes);
        finish();
    }

    private Note find(String id) {
        if (id == null) return null;
        for (Note n : notes) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    private int indexOf(String id) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
