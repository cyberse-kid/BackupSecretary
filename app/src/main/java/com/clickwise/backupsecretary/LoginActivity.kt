package com.clickwise.backupsecretary

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clickwise.backupsecretary.databinding.ActivityLoginBinding
import com.clickwise.backupsecretary.model.LoginRequest
import com.clickwise.backupsecretary.model.DeviceTokenRequest
import com.clickwise.backupsecretary.network.RetrofitClient
import com.clickwise.backupsecretary.util.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            val token = TokenManager.getAccessToken(applicationContext)
            if (!token.isNullOrEmpty()) {
                goToMain()
                return@launch
            }
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                showError("Completa todos los campos")
                return@setOnClickListener
            }

            doLogin(username, password)
        }
    }

    private fun doLogin(username: String, password: String) {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.login(
                    LoginRequest(username, password)
                )

                if (response.isSuccessful) {
                    val body = response.body()!!
                    TokenManager.saveTokens(
                        applicationContext,
                        body.access,
                        body.refresh
                    )
                    registerFCMToken(body.access)
                    goToMain()
                } else {
                    showError("Usuario o contraseña incorrectos")
                }

            } catch (e: Exception) {
                showError("Error de conexión: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun registerFCMToken(accessToken: String) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
            lifecycleScope.launch {
                try {
                    RetrofitClient.api.registerDevice(
                        token = TokenManager.bearerToken(accessToken),
                        request = DeviceTokenRequest(fcmToken)
                    )
                } catch (e: Exception) {
                    // No es crítico
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}