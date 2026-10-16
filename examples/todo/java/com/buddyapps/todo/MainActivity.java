package com.buddyapps.todo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista de tareas "Keep + Notion", sin Gradle y sin XML:
 * toda la interfaz se construye en código Java, solo con el framework de Android.
 *
 *   - Aspecto Keep: rejilla de dos columnas con tarjetas de colores.
 *   - Aspecto Notion: título grande y aire, estado "Hecho/Pendiente".
 *   - Toca una tarjeta para marcarla; mantén pulsado para borrarla.
 *   - Persistencia en filesDir/tasks.json con org.json.
 */
public class MainActivity extends Activity {

    // Paleta estilo Google Keep
    private static final int[] PALETTE = {
            0xFFFFF475, 0xFFCCFF90, 0xFFCBF0F8, 0xFFD7AEFB,
            0xFFFDC69C, 0xFFAECBFA, 0xFFF28B82, 0xFFE6C9A8
    };
    private static final int INK = 0xFF202124;    // texto principal
    private static final int MUTED = 0xFF5F6368;  // texto secundario
    private static final int PAPER = 0xFFF7F7F5;  // fondo tipo Notion
    private static final int ACCENT = 0xFF1A73E8; // azul de acción

    private final List<Task> tasks = new ArrayList<>();
    private LinearLayout col1, col2;
    private EditText input;

    /** Una tarea: texto, estado y color de tarjeta. */
    private static final class Task {
        String text;
        boolean done;
        int color;

        Task(String text, boolean done, int color) {
            this.text = text;
            this.done = done;
            this.color = color;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        load();
        setContentView(buildUi());
        render();
    }

    // ------------------------------------------------------------------
    // Interfaz (todo en código)
    // ------------------------------------------------------------------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(20), dp(24), dp(20), 0);

        // Cabecera tipo Notion
        TextView title = new TextView(this);
        title.setText("Tareas");
        title.setTextSize(30f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Keep + Notion · sin Gradle");
        sub.setTextSize(13f);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(2), 0, dp(16));
        root.addView(sub);

        // Fila de entrada
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        input = new EditText(this);
        input.setHint("Nueva tarea…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(rounded(0xFFFFFFFF, dp(14), 0, 0));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = new TextView(this);
        add.setText("Añadir");
        add.setTextColor(Color.WHITE);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setPadding(dp(18), dp(12), dp(18), dp(12));
        add.setBackground(rounded(ACCENT, dp(14), 0, 0));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.leftMargin = dp(8);
        row.addView(add, addParams);
        root.addView(row);

        // Rejilla de dos columnas (Keep) con scroll
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(16), 0, dp(16));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c1.rightMargin = dp(6);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c2.leftMargin = dp(6);
        columns.addView(col1, c1);
        columns.addView(col2, c2);
        scroll.addView(columns);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        add.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Escribe una tarea", Toast.LENGTH_SHORT).show();
                return;
            }
            tasks.add(0, new Task(text, false, PALETTE[tasks.size() % PALETTE.length]));
            input.setText("");
            save();
            render();
        });

        return root;
    }

    /** Redibuja la rejilla a partir de la lista de tareas. */
    private void render() {
        col1.removeAllViews();
        col2.removeAllViews();

        if (tasks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sin tareas.\nAñade la primera arriba.");
            empty.setTextColor(MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(48), 0, 0);
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            col1.addView(empty);
            return;
        }

        for (int i = 0; i < tasks.size(); i++) {
            (i % 2 == 0 ? col1 : col2).addView(card(tasks.get(i)));
        }
    }

    private View card(final Task task) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(task.color, dp(16), 0x22000000, 1));
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);

        TextView text = new TextView(this);
        text.setText(task.text);
        text.setTextSize(16f);
        text.setTextColor(INK);
        if (task.done) {
            text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            text.setAlpha(0.5f);
        }
        card.addView(text);

        TextView state = new TextView(this);
        state.setText(task.done ? "✓ Hecho" : "○ Pendiente");
        state.setTextSize(12f);
        state.setTextColor(MUTED);
        state.setPadding(0, dp(8), 0, 0);
        card.addView(state);

        card.setOnClickListener(v -> {
            task.done = !task.done;
            save();
            render();
        });
        card.setOnLongClickListener(v -> {
            tasks.remove(task);
            save();
            render();
            Toast.makeText(this, "Tarea borrada", Toast.LENGTH_SHORT).show();
            return true;
        });
        return card;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private GradientDrawable rounded(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        if (strokeWidth > 0) {
            shape.setStroke(strokeWidth, strokeColor);
        }
        return shape;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // Persistencia: filesDir/tasks.json (org.json, sin dependencias)
    // ------------------------------------------------------------------

    private File store() {
        return new File(getFilesDir(), "tasks.json");
    }

    private void load() {
        File file = store();
        if (!file.exists()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            JSONArray array = new JSONArray(new String(buffer, 0, Math.max(read, 0), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                tasks.add(new Task(
                        o.getString("text"),
                        o.optBoolean("done", false),
                        o.optInt("color", PALETTE[i % PALETTE.length])));
            }
        } catch (Exception ignored) {
            // Si el archivo está corrupto, se empieza de cero.
        }
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Task task : tasks) {
                JSONObject o = new JSONObject();
                o.put("text", task.text);
                o.put("done", task.done);
                o.put("color", task.color);
                array.put(o);
            }
            try (FileOutputStream out = new FileOutputStream(store())) {
                out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Sin almacenamiento no hay persistencia, pero la app sigue usable.
        }
    }
}
