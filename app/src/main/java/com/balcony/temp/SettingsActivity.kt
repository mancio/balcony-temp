package com.balcony.temp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.balcony.temp.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user enter or change the database key (the Firebase Realtime Database URL).
 * Also doubles as the first-run setup screen when no key has been saved yet.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val firstRun = !Prefs.hasKey(this)
        binding.settingsDesc.setText(
            if (firstRun) R.string.settings_first_run else R.string.settings_desc
        )
        binding.keyInput.setText(Prefs.getKey(this) ?: "")

        binding.saveButton.setOnClickListener { save() }
        binding.testButton.setOnClickListener { test() }
    }

    private fun currentInput(): String = binding.keyInput.text?.toString()?.trim().orEmpty()

    private fun save() {
        val key = currentInput()
        if (key.isEmpty()) {
            binding.keyInput.error = getString(R.string.key_required)
            return
        }
        Prefs.setKey(this, key)
        TempWidgetProvider.refreshAll(this)
        Toast.makeText(this, R.string.key_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun test() {
        val key = currentInput()
        if (key.isEmpty()) {
            binding.keyInput.error = getString(R.string.key_required)
            return
        }
        binding.testButton.isEnabled = false
        binding.testStatus.visibility = View.VISIBLE
        binding.testStatus.text = getString(R.string.testing)

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TempRepository.fetch(key) }
            }
            binding.testButton.isEnabled = true
            result.onSuccess { data ->
                binding.testStatus.text = getString(R.string.test_ok, data.temperature)
            }.onFailure { error ->
                binding.testStatus.text =
                    getString(R.string.test_fail, error.localizedMessage ?: "error")
            }
        }
    }
}
