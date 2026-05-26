package com.clickwise.backupsecretary

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.clickwise.backupsecretary.databinding.ActivityMainBinding
import com.clickwise.backupsecretary.network.RetrofitClient
import com.clickwise.backupsecretary.service.WhatsAppAccessibilityService
import com.clickwise.backupsecretary.service.WhatsAppNotificationListenerService
import com.clickwise.backupsecretary.ui.LeadsAdapter
import com.clickwise.backupsecretary.util.TokenManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var leadsAdapter: LeadsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        loadLeads()
        checkBotPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadLeads()
        checkBotPermissions()
    }

    private fun setupRecyclerView() {
        leadsAdapter = LeadsAdapter()
        binding.rvLeads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = leadsAdapter
        }
    }

    private fun setupButtons() {
        binding.btnActivateBot.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnActivateNotifications.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadLeads()
        }
    }

    private fun loadLeads() {
        lifecycleScope.launch {
            try {
                val token = TokenManager.getAccessToken(applicationContext) ?: return@launch
                val response = RetrofitClient.api.getLeads(
                    token = TokenManager.bearerToken(token)
                )
                if (response.isSuccessful) {
                    val leads = response.body()?.results ?: emptyList()
                    leadsAdapter.submitList(leads)
                    binding.tvEmptyState.visibility =
                        if (leads.isEmpty()) android.view.View.VISIBLE
                        else android.view.View.GONE
                }
            } catch (e: Exception) {
                // Network errors are surfaced by keeping the current list.
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun checkBotPermissions() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val notificationsEnabled = isNotificationListenerEnabled()

        binding.tvBotStatus.text = when {
            accessibilityEnabled && notificationsEnabled -> "Bot activo: WhatsApp abierto y bloqueado"
            accessibilityEnabled -> "Bot activo solo con WhatsApp abierto"
            notificationsEnabled -> "Bot activo con pantalla bloqueada"
            else -> "Bot inactivo"
        }

        binding.btnActivateBot.text = if (accessibilityEnabled) {
            "Accesibilidad activa"
        } else {
            "Activar accesibilidad"
        }
        binding.btnActivateBot.isEnabled = !accessibilityEnabled

        binding.btnActivateNotifications.text = if (notificationsEnabled) {
            "Notificaciones activas"
        } else {
            "Activar notificaciones"
        }
        binding.btnActivateNotifications.isEnabled = !notificationsEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(
            this,
            WhatsAppAccessibilityService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(":")
            .any { it.equals(expectedService, ignoreCase = true) }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val expectedService = ComponentName(
            this,
            WhatsAppNotificationListenerService::class.java
        ).flattenToString()

        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return enabledListeners
            .split(":")
            .any { it.equals(expectedService, ignoreCase = true) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch {
                    TokenManager.clearTokens(applicationContext)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
