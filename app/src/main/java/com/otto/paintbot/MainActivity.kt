package com.otto.paintbot

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val gamePackage = "com.pbn.pbox.oakever.coloring.drawing.paint.by.number.colour.art"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openGameButton = findViewById<Button>(R.id.openGameButton)
        openGameButton.setOnClickListener {
            launchGame()
        }
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage(gamePackage)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Paint by Number is not installed.", Toast.LENGTH_SHORT).show()
        }
    }
}
