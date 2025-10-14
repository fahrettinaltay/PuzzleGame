package com.mey.puzzlegame

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton

class StartActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        // apply saved theme BEFORE setContentView
        val dark = prefs.getBoolean("dark", false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val toggle = findViewById<ToggleButton>(R.id.toggleTheme)
        toggle.isChecked = prefs.getBoolean("dark", false)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // recreate activity so theme takes effect everywhere
            recreate()
        }

        findViewById<MaterialButton>(R.id.btnEasy).setOnClickListener { startPuzzle(3) }
        findViewById<MaterialButton>(R.id.btnMedium).setOnClickListener { startPuzzle(4) }
        findViewById<MaterialButton>(R.id.btnHard).setOnClickListener { startPuzzle(5) }
    }

    private fun startPuzzle(size: Int) {
        startActivity(Intent(this, PuzzleActivity::class.java).putExtra("SIZE", size))
    }
}