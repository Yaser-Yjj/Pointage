package com.lykos.pointage.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lykos.pointage.R

class DailyReportActivity : AppCompatActivity() {

    private lateinit var editTextNote: EditText
    private lateinit var buttonNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_report)
        enableEdgeToEdge()
        setContentView(R.layout.activity_daily_report)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dailyReport)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editTextNote = findViewById(R.id.editTextNote)
        buttonNext = findViewById(R.id.buttonNext)

        buttonNext.setOnClickListener {
            val note = editTextNote.text.toString().trim()

            if (note.isEmpty()) {
                editTextNote.error = "Note required"
                return@setOnClickListener
            }

            val intent = Intent(this, ImageUploadActivity::class.java)
            intent.putExtra("report_text", note)
            startActivity(intent)
        }
    }
}