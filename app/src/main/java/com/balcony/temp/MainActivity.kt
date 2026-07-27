package com.balcony.temp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.balcony.temp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.swipeRefresh.setOnRefreshListener { loadData(fromSwipe = true) }
        binding.refreshButton.setOnClickListener { loadData(fromSwipe = false) }
        binding.addWidgetButton.setOnClickListener { requestPinWidget() }

        loadData(fromSwipe = false)
    }

    override fun onResume() {
        super.onResume()
        TempWidgetProvider.refreshAll(this)
    }

    private fun loadData(fromSwipe: Boolean) {
        if (!fromSwipe) {
            binding.progress.visibility = View.VISIBLE
        }
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TempRepository.fetch() }
            }
            binding.progress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            result.onSuccess { data -> render(data) }
                .onFailure { error ->
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(
                        R.string.error_loading,
                        error.localizedMessage ?: "unknown error"
                    )
                }
        }
    }

    private fun render(data: ThermometerData) {
        binding.content.visibility = View.VISIBLE

        binding.icon.setImageResource(TempRepository.temperatureIconRes(data.temperature))
        binding.temperature.text = if (data.temperature.isNaN()) {
            "--"
        } else {
            getString(R.string.temperature_value, data.temperature)
        }

        binding.updatedAgo.text = TempRepository.timeAgo(data.timeUnix)
        binding.updatedAbsolute.text = getString(
            R.string.last_update_absolute,
            TempRepository.formatTimestamp(data.timeUnix)
        )

        val stale = TempRepository.isStale(data.timeUnix)
        binding.batteryRow.visibility = if (stale) View.GONE else View.VISIBLE
        binding.batteryBar.visibility = if (stale) View.GONE else View.VISIBLE
        binding.voltage.visibility = if (stale) View.GONE else View.VISIBLE
        binding.batteryStale.visibility = if (stale) View.VISIBLE else View.GONE

        if (!stale) {
            val battery = TempRepository.batteryPercentage(data.voltage)
            binding.batteryPercent.text = getString(R.string.battery_value, battery)
            binding.batteryBar.progress = battery
            binding.voltage.text = if (data.voltage.isNaN()) {
                "--"
            } else {
                getString(R.string.voltage_value, data.voltage)
            }
        }
    }

    private fun requestPinWidget() {
        val widgetManager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, TempWidgetProvider::class.java)
        if (widgetManager != null && widgetManager.isRequestPinAppWidgetSupported) {
            widgetManager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}
