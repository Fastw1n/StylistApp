package com.example.app1

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app1.databinding.ActivityMainPageBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity(){

    private lateinit var binding: ActivityMainPageBinding

    private val images = mutableListOf<Uri>()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                images.add(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthStorage.restore(this)

        if (!AuthStorage.isLoggedIn(this)) {
            startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        syncWardrobeOnStartup()

        // стартовый фрагмент
        replaceFragment(HomePageFragment())

        binding.homeButton.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.framelayout)


            if (currentFragment !is HomePageFragment) {
                replaceFragment(HomePageFragment())
            }
        }

        binding.wardrobeButton.setOnClickListener {
            replaceFragment(WardrobePageFragment())
        }

        binding.chatbotButton.setOnClickListener {
            replaceFragment(ChatBotPageFragment())
        }

        binding.settingsButton.setOnClickListener {
            replaceFragment(SettingsPageFragment())
        }

        binding.cameraButton.setOnClickListener {
            replaceFragment(OutfitsPageFragment())
        }

    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.framelayout, fragment)
            .commit()
    }

    private fun syncWardrobeOnStartup() {
        lifecycleScope.launch {
            WardrobeSyncer.syncFromBackend(this@HomeActivity)
        }
    }
}
