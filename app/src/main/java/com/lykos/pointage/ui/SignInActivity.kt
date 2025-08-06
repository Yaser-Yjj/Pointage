package com.lykos.pointage.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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

class SignInActivity : AppCompatActivity() {

    private lateinit var emailOrUsernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordVisibilityToggle: ImageView
    private lateinit var signInButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var createAccountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        // Bind views
        emailOrUsernameInput = findViewById(R.id.emailOrUsernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordVisibilityToggle = findViewById(R.id.passwordVisibilityToggle)
        signInButton = findViewById(R.id.signInButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        createAccountText = findViewById(R.id.createAccountText)

        // Toggle password visibility
        setupPasswordToggle()

        // Click listeners
        signInButton.setOnClickListener { attemptLogin() }
        forgotPasswordText.setOnClickListener {
            Toast.makeText(this, "Forgot Password feature coming soon", Toast.LENGTH_SHORT).show()
        }
        createAccountText.setOnClickListener {
            Toast.makeText(this, "Account creation not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPasswordToggle() {
        var isPasswordVisible = false
        passwordVisibilityToggle.setImageResource(R.drawable.eye_slash)

        passwordVisibilityToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.inputType = 1 // textVisiblePassword
                passwordVisibilityToggle.setImageResource(R.drawable.eye_open)
            } else {
                passwordInput.inputType = 0x81  // textPassword
                passwordVisibilityToggle.setImageResource(R.drawable.eye_slash)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun attemptLogin() {
        val username = emailOrUsernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validate input
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

        // Disable button during login
        signInButton.isEnabled = false
        signInButton.text = "Signing in..."

        // Launch login request
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val loginRequest = LoginData(username, password)
                val response = RetrofitClient.apiService.login(loginRequest)

                withContext(Dispatchers.Main) {
                    signInButton.isEnabled = true
                    signInButton.text = "Sign In"

                    if (response.isSuccessful && response.body()?.userId != null) {
                        val userId = response.body()?.userId!!
                        Toast.makeText(this@SignInActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                        getSharedPreferences("AppPrefs", MODE_PRIVATE)
                            .edit {
                                putString("user_id", userId)
                            }

                        // Go to MainActivity
                        val intent = Intent(this@SignInActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMsg = response.body()?.message ?: "Invalid username or password"
                        Toast.makeText(this@SignInActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    signInButton.isEnabled = true
                    signInButton.text = "Sign In"
                    Toast.makeText(this@SignInActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}