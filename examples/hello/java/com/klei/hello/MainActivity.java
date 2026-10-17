package com.klei.hello;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("Hola desde build-apk.sh en Alpine");
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
    }
}
