package com.example.app1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.app1.databinding.ActivityHelloPageBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityHelloPageBinding? = null
    private val binding
        get() = _binding ?: throw IllegalStateException("Binding must not be null")

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthStorage.restore(this)

        if (AuthStorage.isLoggedIn(this)) {
            openHome()
            return
        }

        _binding = ActivityHelloPageBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hello)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.authPrimaryButton.setOnClickListener {
            submitAuth()
        }

        binding.authSecondaryButton.setOnClickListener {
            isRegisterMode = !isRegisterMode
            renderMode()
        }

        renderMode()
    }

    private fun renderMode() {
        binding.authTitleText.text = if (isRegisterMode) "Регистрация" else "Вход"
        binding.authSubtitleText.text = if (isRegisterMode) {
            "Создайте профиль, чтобы гардероб сохранялся за вами"
        } else {
            "Войдите, чтобы открыть свой гардероб"
        }
        binding.nameInputLayout.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
        binding.authPrimaryButton.text = if (isRegisterMode) "Зарегистрироваться" else "Войти"
        binding.authSecondaryButton.text = if (isRegisterMode) "Уже есть профиль? Войти" else "Нет профиля? Зарегистрироваться"
    }

    private fun submitAuth() {
        val email = binding.emailEditText.text?.toString().orEmpty().trim()
        val password = binding.passwordEditText.text?.toString().orEmpty()
        val name = binding.nameEditText.text?.toString().orEmpty().trim().ifBlank { null }

        if (email.isBlank()) {
            binding.emailInputLayout.error = "Введите почту"
            return
        }

        if (password.length < 6) {
            binding.passwordInputLayout.error = "Минимум 6 символов"
            return
        }

        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.authPrimaryButton.isEnabled = false

        lifecycleScope.launch {
            runCatching {
                if (isRegisterMode) {
                    RetrofitClient.api.register(RegisterRequest(email = email, password = password, name = name))
                } else {
                    RetrofitClient.api.login(AuthRequest(email = email, password = password))
                }
            }.onSuccess { response ->
                WardrobeContainer.clear(this@MainActivity)
                AuthStorage.save(this@MainActivity, response)
                WeatherPreferences.clearLegacy(this@MainActivity)
                WardrobeSyncer.syncFromBackend(this@MainActivity)
                openHome()
            }.onFailure { error ->
                binding.authPrimaryButton.isEnabled = true
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Не удалось выполнить вход",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openHome() {
        startActivity(Intent(this@MainActivity, HomeActivity::class.java))
        finish()
    }
}
