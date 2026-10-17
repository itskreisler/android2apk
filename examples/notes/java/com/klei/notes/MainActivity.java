package com.klei.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Keep-style staggered grid of notes. Long-press a card to delete; tap to edit. */
public class MainActivity extends Activity {
    private final List<Note> notes = new ArrayList<>();
    private NoteStore store;
    private LinearLayout col1, col2;
    private TextView count, empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new NoteStore(this);
        setContentView(R.layout.activity_main);
        col1 = findViewById(R.id.col1);
        col2 = findViewById(R.id.col2);
        count = findViewById(R.id.count);
        empty = findViewById(R.id.empty);
        ImageButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        notes.clear();
        notes.addAll(store.load());
        Collections.sort(notes, (a, b) -> Long.compare(b.updated, a.updated));
        col1.removeAllViews();
        col2.removeAllViews();
        empty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        count.setText(notes.isEmpty() ? "" : (notes.size() == 1 ? "1 nota" : notes.size() + " notas"));
        int i = 0;
        for (Note n : notes) {
            (i % 2 == 0 ? col1 : col2).addView(card(n));
            i++;
        }
    }

    private View card(Note n) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(n.color);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x14000000);
        box.setBackground(bg);
        int p = dp(14);
        box.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        box.setLayoutParams(lp);

        if (!n.title.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(n.title);
            t.setTextSize(16);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextColor(0xFF1A1A1A);
            box.addView(t);
        }
        if (!n.body.isEmpty()) {
            TextView bd = new TextView(this);
            bd.setText(n.body);
            bd.setTextSize(14);
            bd.setTextColor(0xFF3D3D3D);
            bd.setPadding(0, dp(4), 0, 0);
            box.addView(bd);
        }
        TextView meta = new TextView(this);
        meta.setText(stamp(n.updated));
        meta.setTextSize(11);
        meta.setTextColor(0x8A000000);
        meta.setPadding(0, dp(8), 0, 0);
        box.addView(meta);

        box.setOnClickListener(v -> {
            Intent i = new Intent(this, EditActivity.class);
            i.putExtra("id", n.id);
            startActivity(i);
        });
        box.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(R.string.delete_confirm)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        notes.remove(n);
                        store.save(notes);
                        refresh();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        return box;
    }

    private String stamp(long ms) {
        Date d = new Date(ms);
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        boolean today = day.format(d).equals(day.format(new Date()));
        return new SimpleDateFormat(today ? "HH:mm" : "d MMM", Locale.getDefault()).format(d);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
