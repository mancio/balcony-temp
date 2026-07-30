package com.balcony.temp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.balcony.temp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val autoRefresh = object : Runnable {
        override fun run() {
            loadData(fromSwipe = false)
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.swipeRefresh.setOnRefreshListener { loadData(fromSwipe = true) }
        binding.refreshButton.setOnClickListener { loadData(fromSwipe = false) }
        binding.addWidgetButton.setOnClickListener { requestPinWidget() }

        // Re-arm background refresh: a force-stop cancels WorkManager work, and opening the
        // app is the only reliable moment to bring it back.
        TempRefreshWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        loadData(fromSwipe = false)
        TempWidgetProvider.refreshAll(this)
        refreshHandler.removeCallbacks(autoRefresh)
        refreshHandler.postDelayed(autoRefresh, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(autoRefresh)
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
        TempCache.save(this, data)

        binding.icon.setImageResource(TempRepository.temperatureIconRes(data.temperature))
        binding.temperature.text = if (data.temperature.isNaN()) {
            "--"
        } else {
            getString(R.string.temperature_value, data.temperature)
        }

        val stale = TempRepository.isStale(data.timeUnix)
        binding.updatedAgo.text = TempRepository.timeAgo(data.timeUnix)
        binding.updatedAgo.setTextColor(
            ContextCompat.getColor(this, if (stale) R.color.error else R.color.accent)
        )
        binding.updatedAbsolute.text = getString(
            R.string.last_update_absolute,
            TempRepository.formatTimestamp(data.timeUnix)
        )
        binding.batteryStale.visibility = if (stale) View.VISIBLE else View.GONE

        // The battery block stays visible even when the reading is stale: a flat battery is
        // the most likely reason the thermometer stopped reporting in the first place.
        val battery = TempRepository.batteryPercentage(data.voltage)
        val lowBattery = TempRepository.isLowBattery(data.voltage)
        val batteryColor =
            ContextCompat.getColor(this, if (lowBattery) R.color.error else R.color.text_primary)

        binding.batteryPercent.text = if (data.voltage.isNaN()) {
            "--"
        } else {
            getString(R.string.battery_value, battery)
        }
        binding.batteryPercent.setTextColor(batteryColor)
        binding.batteryBar.progress = battery
        binding.batteryBar.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (lowBattery) R.color.error else R.color.accent)
        )
        binding.voltage.text = if (data.voltage.isNaN()) {
            "--"
        } else {
            getString(R.string.voltage_value, data.voltage)
        }
        binding.batteryLow.visibility = if (lowBattery) View.VISIBLE else View.GONE
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

    companion object {
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }
}
