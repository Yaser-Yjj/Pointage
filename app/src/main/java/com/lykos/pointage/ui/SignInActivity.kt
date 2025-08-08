package com.lykos.pointage.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.R
import com.lykos.pointage.model.data.LoginData
import com.lykos.pointage.service.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import com.lykos.pointage.utils.PreferencesManager

class SignInActivity : AppCompatActivity() {

    private lateinit var emailOrUsernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordVisibilityToggle: ImageView
    private lateinit var signInButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var createAccountText: TextView
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        preferencesManager = PreferencesManager(this)

        emailOrUsernameInput = findViewById(R.id.emailOrUsernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordVisibilityToggle = findViewById(R.id.passwordVisibilityToggle)
        signInButton = findViewById(R.id.signInButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        createAccountText = findViewById(R.id.createAccountText)

        setupPasswordToggle()

        signInButton.setOnClickListener { attemptLogin() }
        forgotPasswordText.setOnClickListener {
            Toast.makeText(this, "Forgot Password feature coming soon", Toast.LENGTH_SHORT).show()
        }
        createAccountText.setOnClickListener {
            Toast.makeText(this, "Please contact your administrator to create an account for you.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPasswordToggle() {
        var isPasswordVisible = false
        passwordVisibilityToggle.setImageResource(R.drawable.eye_slash)

        passwordVisibilityToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.inputType = 1
                passwordVisibilityToggle.setImageResource(R.drawable.eye_open)
            } else {
                passwordInput.inputType = 0x81
                passwordVisibilityToggle.setImageResource(R.drawable.eye_slash)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun attemptLogin() {
        val username = emailOrUsernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (username.isEmpty()) {
            emailOrUsernameInput.error = "Username is required"
            emailOrUsernameInput.requestFocus()
            return
        }
        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            passwordInput.requestFocus()
            return
        }

        signInButton.isEnabled = false
        signInButton.text = "Signing in..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val loginRequest = LoginData(username, password)
                val dynamicUrl = "https://boombatours.com/pointage_app/public/api/userlogin"
                val response = RetrofitClient.apiService.login(dynamicUrl, loginRequest)

                withContext(Dispatchers.Main) {
                    signInButton.isEnabled = true
                    signInButton.text = "Sign In"

                    if (response.isSuccessful) {
                        val body = response.body()
                        val userId = body?.userId
                        Log.d("Login System", "attemptLogin: $userId ")
                        if (!userId.isNullOrEmpty()) {
                            Toast.makeText(this@SignInActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                            preferencesManager.saveCurrentUserId(userId)

                            val intent = Intent(this@SignInActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@SignInActivity, "Invalid response from server", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMsg = response.body()?.message ?: "Invalid username or password"
                        Toast.makeText(this@SignInActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    signInButton.isEnabled = true
                    signInButton.text = "Sign In"
                    Toast.makeText(
                        this@SignInActivity, "Network error: ${e.message}", Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}