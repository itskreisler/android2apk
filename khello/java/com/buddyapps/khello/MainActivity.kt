package com.buddyapps.khello

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this).apply {
            text = "Hola desde Kotlin, sin Gradle"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        setContentView(t)
    }
}
