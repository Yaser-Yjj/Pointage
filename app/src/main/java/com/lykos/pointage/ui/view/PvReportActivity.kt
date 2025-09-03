package com.lykos.pointage.ui.view

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.R
import com.lykos.pointage.service.RetrofitClient
import com.lykos.pointage.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class PvReportActivity : AppCompatActivity() {

    private lateinit var userID: String
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var editTextNote: EditText
    private lateinit var buttonNext: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pv_report)
        preferencesManager = PreferencesManager(this)

        userID = preferencesManager.getCurrentUserId().toString()

        editTextNote = findViewById(R.id.editTextPvNote)
        buttonNext = findViewById(R.id.buttonSubmit)

        buttonNext.setOnClickListener {

            val note = editTextNote.text.toString().trim()

            if (note.isEmpty()) {
                editTextNote.error = "Note required"
                return@setOnClickListener
            }

            sendPvReport(userID, note)
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun sendPvReport(userId: String, note: String) {
        buttonNext.isEnabled = false

        val userIdPart = userId.toRequestBody("text/plain".toMediaTypeOrNull())
        val notePart = note.toRequestBody("text/plain".toMediaTypeOrNull())

        lifecycleScope.launch(Dispatchers.IO) {
            val response = RetrofitClient.apiService.postPvReport(userIdPart, notePart)

            withContext(Dispatchers.Main) {
                buttonNext.isEnabled = true
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(
                        this@PvReportActivity, "PV report added successfully", Toast.LENGTH_SHORT
                    ).show()
                    editTextNote.setText("")

                } else {
                    Toast.makeText(
                        this@PvReportActivity,
                        "❌ Error: ${response.body()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
